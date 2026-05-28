package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.sales.domain.dto.ApplyCreditNoteRequestDto;
import com.orbix.engine.modules.sales.domain.dto.CreateCustomerReturnRequestDto;
import com.orbix.engine.modules.sales.domain.dto.CreditNoteAllocationDto;
import com.orbix.engine.modules.sales.domain.dto.CustomerCreditNoteDto;
import com.orbix.engine.modules.sales.domain.dto.CustomerReturnDto;
import com.orbix.engine.modules.sales.domain.dto.IssueCreditNoteRequestDto;
import com.orbix.engine.modules.sales.domain.entity.CustomerCreditNote;
import com.orbix.engine.modules.sales.domain.entity.CustomerCreditNoteAllocation;
import com.orbix.engine.modules.sales.domain.entity.CustomerReturn;
import com.orbix.engine.modules.sales.domain.entity.CustomerReturnLine;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.sales.domain.enums.CreditNoteStatus;
import com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus;
import com.orbix.engine.modules.sales.domain.event.CustomerCreditNoteApplied;
import com.orbix.engine.modules.sales.repository.CustomerCreditNoteAllocationRepository;
import com.orbix.engine.modules.sales.repository.CustomerCreditNoteRepository;
import com.orbix.engine.modules.sales.repository.CustomerReturnLineRepository;
import com.orbix.engine.modules.sales.repository.CustomerReturnRepository;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import com.orbix.engine.modules.sales.service.SalesInvoiceService;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.service.StockMoveService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CustomerReturnServiceImpl implements CustomerReturnService {

    private static final int MONEY_SCALE = 4;
    private static final String AGG_RETURN = "CustomerReturn";
    private static final String AGG_CN = "CustomerCreditNote";
    private static final String F_RETURN_ID = "customerReturnId";
    private static final String F_CN_ID = "customerCreditNoteId";
    private static final String F_NUMBER = "number";
    private static final String F_CUSTOMER_ID = "customerId";
    private static final String F_TOTAL_AMOUNT = "totalAmount";

    private final CustomerReturnRepository returns;
    private final CustomerReturnLineRepository lines;
    private final CustomerCreditNoteRepository creditNotes;
    private final CustomerCreditNoteAllocationRepository allocations;
    private final SalesInvoiceRepository invoices;
    private final SalesInvoiceService salesInvoiceService;
    private final ItemRepository items;
    private final VatGroupRepository vatGroups;
    private final AppUserRepository users;
    private final StockMoveService stockMoveService;
    private final DayGuard dayGuard;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = AGG_RETURN)
    public CustomerReturnDto createDraft(CreateCustomerReturnRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        branchScope.requireAccess(request.branchId());
        String number = request.number().trim().toUpperCase();
        if (returns.existsByBranchIdAndNumber(request.branchId(), number)) {
            throw new IllegalArgumentException(
                "Customer return number already exists for this branch: " + number);
        }
        if (request.originalInvoiceId() != null) {
            SalesInvoice invoice = invoices.findById(request.originalInvoiceId())
                .orElseThrow(() -> new NoSuchElementException(
                    "Original invoice not found: " + request.originalInvoiceId()));
            if (!Objects.equals(invoice.getCompanyId(), companyId)) {
                throw new NoSuchElementException(
                    "Original invoice not found: " + request.originalInvoiceId());
            }
            if (!Objects.equals(invoice.getCustomerId(), request.customerId())) {
                throw new IllegalArgumentException(
                    "Original invoice belongs to a different customer");
            }
        }

        CustomerReturn ret = returns.save(new CustomerReturn(
            number, companyId, request.branchId(), request.customerId(),
            request.originalInvoiceId(), request.returnDate(), request.reason(),
            request.restock(), request.notes(), actorId
        ));
        List<CustomerReturnLine> savedLines = saveLinesAndRollUp(ret, request.lines(), companyId);

        events.publish("CustomerReturnCreated.v1", AGG_RETURN, String.valueOf(ret.getId()),
            Map.of(F_RETURN_ID, ret.getId(), F_NUMBER, ret.getNumber(),
                F_CUSTOMER_ID, ret.getCustomerId(),
                F_TOTAL_AMOUNT, ret.getTotalAmount(),
                "reason", ret.getReason().name(),
                "restock", ret.isRestock()));
        return CustomerReturnDto.from(ret, savedLines);
    }

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG_RETURN)
    public CustomerReturnDto post(String uid) {
        CustomerReturn ret = requireReturnByUid(uid);
        dayGuard.requireOpenDay(ret.getBranchId());
        Long actorId = context.userId();
        Long companyId = context.companyId();
        List<CustomerReturnLine> returnLines = lines.findByCustomerReturnIdOrderByLineNoAsc(ret.getId());

        StockMoveType moveType = ret.isRestock() ? StockMoveType.RETURN_IN : StockMoveType.DAMAGE;
        for (CustomerReturnLine line : returnLines) {
            Item item = requireItem(line.getItemId(), companyId);
            if (item.isBatchTracked()) {
                throw new IllegalArgumentException(
                    "Cannot post a customer return with batch-tracked items in F4.4 (item " + item.getCode()
                        + "); manual batch-routed restock will land with a later slice");
            }
            BigDecimal qty = ret.isRestock() ? line.getReturnedQty() : line.getReturnedQty().negate();
            stockMoveService.post(new PostStockMoveRequestDto(
                line.getItemId(), ret.getBranchId(),
                qty, line.getUnitPrice(),
                moveType, AGG_RETURN, ret.getId(),
                ret.getReason().name(), false, null
            ));
        }
        ret.post(actorId);
        events.publish("CustomerReturnPosted.v1", AGG_RETURN, String.valueOf(ret.getId()),
            Map.of(F_RETURN_ID, ret.getId(), F_NUMBER, ret.getNumber(),
                F_CUSTOMER_ID, ret.getCustomerId(),
                "branchId", ret.getBranchId(),
                F_TOTAL_AMOUNT, ret.getTotalAmount(),
                "moveType", moveType.name()));
        return CustomerReturnDto.from(ret, returnLines);
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL", entityType = AGG_RETURN)
    public CustomerReturnDto cancel(String uid) {
        CustomerReturn ret = requireReturnByUid(uid);
        ret.cancel(context.userId());
        events.publish("CustomerReturnCancelled.v1", AGG_RETURN, String.valueOf(ret.getId()),
            Map.of(F_RETURN_ID, ret.getId(), F_NUMBER, ret.getNumber()));
        return CustomerReturnDto.from(ret, lines.findByCustomerReturnIdOrderByLineNoAsc(ret.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "ISSUE_CREDIT", entityType = AGG_CN)
    public CustomerCreditNoteDto issueCreditNote(String uid, IssueCreditNoteRequestDto request) {
        CustomerReturn ret = requireReturnByUid(uid);
        String number = request.number().trim().toUpperCase();
        if (creditNotes.existsByBranchIdAndNumber(ret.getBranchId(), number)) {
            throw new IllegalArgumentException(
                "Credit-note number already exists for this branch: " + number);
        }
        // Use the original invoice's currency when present, else fall back to TZS — kept simple for now.
        String currencyCode = ret.getOriginalInvoiceId() != null
            ? invoices.findById(ret.getOriginalInvoiceId()).map(SalesInvoice::getCurrencyCode).orElse("TZS")
            : "TZS";
        CustomerCreditNote cn = creditNotes.save(new CustomerCreditNote(
            number, ret.getCompanyId(), ret.getBranchId(), ret.getCustomerId(),
            ret.getId(), ret.getReturnDate(), currencyCode,
            ret.getTotalAmount(), request.notes(), context.userId()
        ));
        ret.markCredited(context.userId());

        events.publish("CustomerCreditNoteIssued.v1", AGG_CN, String.valueOf(cn.getId()),
            Map.of(F_CN_ID, cn.getId(), F_NUMBER, cn.getNumber(),
                F_CUSTOMER_ID, cn.getCustomerId(),
                F_RETURN_ID, ret.getId(),
                F_TOTAL_AMOUNT, cn.getTotalAmount()));
        return CustomerCreditNoteDto.from(cn);
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<CustomerReturnDto> list(Long branchId, Pageable pageable) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        Page<CustomerReturn> page = scope == null
            ? returns.findByCompanyIdOrderByIdDesc(companyId, pageable)
            : returns.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, scope, pageable);
        return PageDto.of(page, r -> CustomerReturnDto.from(r, lines.findByCustomerReturnIdOrderByLineNoAsc(r.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerReturnDto get(String uid) {
        CustomerReturn ret = requireReturnByUid(uid);
        return CustomerReturnDto.from(ret, lines.findByCustomerReturnIdOrderByLineNoAsc(ret.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerCreditNoteDto> listCreditNotes(Long branchId) {
        Long companyId = context.companyId();
        return creditNotes.findByCompanyIdOrderByIdDesc(companyId).stream()
            .filter(c -> branchId == null || Objects.equals(c.getBranchId(), branchId))
            .map(CustomerCreditNoteDto::from)
            .toList();
    }

    private List<CustomerReturnLine> saveLinesAndRollUp(CustomerReturn ret,
                                                        List<CreateCustomerReturnRequestDto.Line> requestLines,
                                                        Long companyId) {
        List<CustomerReturnLine> saved = new ArrayList<>(requestLines.size());
        BigDecimal total = BigDecimal.ZERO;
        int lineNo = 1;
        for (CreateCustomerReturnRequestDto.Line input : requestLines) {
            Item item = requireItem(input.itemId(), companyId);
            Long uomId = input.uomId() != null ? input.uomId() : item.getUomId();
            Long vatGroupId = input.vatGroupId() != null ? input.vatGroupId() : item.getVatGroupId();
            VatGroup vat = requireVatGroup(vatGroupId, companyId);
            BigDecimal net = input.returnedQty().multiply(input.unitPrice())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal tax = net.multiply(vat.getRate())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal lineTotal = net.add(tax);

            CustomerReturnLine line = lines.save(new CustomerReturnLine(
                ret.getId(), lineNo++, input.itemId(), uomId,
                input.returnedQty(), input.unitPrice(), vatGroupId,
                tax, lineTotal, input.originalLineId()
            ));
            saved.add(line);
            total = total.add(lineTotal);
        }
        ret.rollUpTotal(total);
        return saved;
    }

    private CustomerReturn requireReturnByUid(String uid) {
        CustomerReturn ret = returns.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Customer return not found: " + uid));
        if (!Objects.equals(ret.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Customer return not found: " + uid);
        }
        branchScope.requireAccess(ret.getBranchId());
        return ret;
    }

    private Item requireItem(Long itemId, Long companyId) {
        Item item = items.findById(itemId)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (!Objects.equals(item.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Item not found: " + itemId);
        }
        return item;
    }

    private VatGroup requireVatGroup(Long vatGroupId, Long companyId) {
        VatGroup vat = vatGroups.findById(vatGroupId)
            .orElseThrow(() -> new NoSuchElementException("VAT group not found: " + vatGroupId));
        if (!Objects.equals(vat.getCompanyId(), companyId)) {
            throw new NoSuchElementException("VAT group not found: " + vatGroupId);
        }
        return vat;
    }

    // ------------------------------------------------------------------ Slice H

    @Override
    @Transactional
    @Auditable(action = "APPLY_CREDIT", entityType = AGG_CN)
    public CustomerCreditNoteDto applyToInvoice(String creditNoteUid, ApplyCreditNoteRequestDto req) {
        Long companyId = context.companyId();
        Long actorId   = context.userId();

        // 1. Load + validate credit note.
        CustomerCreditNote cn = requireCreditNoteByUid(creditNoteUid, companyId);
        if (cn.getStatus() != CreditNoteStatus.POSTED
                && cn.getStatus() != CreditNoteStatus.PARTIALLY_ALLOCATED) {
            throw new IllegalStateException(
                "Credit note " + creditNoteUid + " is " + cn.getStatus()
                    + "; only POSTED or PARTIALLY_ALLOCATED notes can be applied");
        }

        // 2. Validate amount ≤ available.
        BigDecimal available = cn.getTotalAmount().subtract(cn.getAllocatedAmount());
        if (req.amount().compareTo(available) > 0) {
            throw new IllegalArgumentException(
                "Apply amount " + req.amount() + " exceeds available credit " + available
                    + " on credit note " + creditNoteUid);
        }

        // 3. Load + validate target invoice.
        SalesInvoice invoice = invoices.findByUid(req.salesInvoiceUid())
            .orElseThrow(() -> new NoSuchElementException(
                "Sales invoice not found: " + req.salesInvoiceUid()));
        if (!Objects.equals(invoice.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Sales invoice not found: " + req.salesInvoiceUid());
        }
        if (!Objects.equals(invoice.getCustomerId(), cn.getCustomerId())) {
            throw new IllegalArgumentException(
                "Sales invoice belongs to a different customer than the credit note");
        }
        if (invoice.getStatus() != SalesInvoiceStatus.POSTED
                && invoice.getStatus() != SalesInvoiceStatus.PARTIALLY_PAID) {
            throw new IllegalArgumentException(
                "Sales invoice " + req.salesInvoiceUid() + " is " + invoice.getStatus()
                    + "; only POSTED or PARTIALLY_PAID invoices can receive a credit-note apply");
        }
        BigDecimal invoiceOutstanding = invoice.getTotalAmount().subtract(invoice.getPaidAmount());
        if (req.amount().compareTo(invoiceOutstanding) > 0) {
            throw new IllegalArgumentException(
                "Apply amount " + req.amount() + " exceeds invoice outstanding "
                    + invoiceOutstanding + " on invoice " + req.salesInvoiceUid());
        }

        // 4. Persist allocation row.
        CustomerCreditNoteAllocation alloc = CustomerCreditNoteAllocation.builder()
            .customerCreditNoteId(cn.getId())
            .salesInvoiceId(invoice.getId())
            .amount(req.amount())
            .allocatedAt(Instant.now())
            .allocatedBy(actorId)
            .build();
        allocations.save(alloc);

        // 5. Update credit-note allocated amount + status.
        BigDecimal newAllocated = cn.getAllocatedAmount().add(req.amount());
        cn.setAllocatedAmount(newAllocated);
        cn.setUpdatedAt(Instant.now());
        cn.setUpdatedBy(actorId);
        cn.setStatus(newAllocated.compareTo(cn.getTotalAmount()) == 0
            ? CreditNoteStatus.FULLY_ALLOCATED
            : CreditNoteStatus.PARTIALLY_ALLOCATED);

        // 6. Apply credit to invoice in same TX (ADR-0004 exemption #21).
        salesInvoiceService.applyCreditNote(invoice.getId(), req.amount());

        // 7. Emit outbox event.
        events.publish(CustomerCreditNoteApplied.TYPE, AGG_CN, String.valueOf(cn.getId()),
            new CustomerCreditNoteApplied(
                cn.getUid(), req.salesInvoiceUid(),
                req.amount(), cn.getCurrencyCode(), actorId
            ).toPayload());

        // 8. Return hydrated DTO.
        return hydrateCrediNote(cn);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerCreditNoteDto getCreditNote(String uid) {
        Long companyId = context.companyId();
        CustomerCreditNote cn = requireCreditNoteByUid(uid, companyId);
        return hydrateCrediNote(cn);
    }

    // ----- credit-note private helpers -----------------------------------

    private CustomerCreditNote requireCreditNoteByUid(String uid, Long companyId) {
        CustomerCreditNote cn = creditNotes.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Credit note not found: " + uid));
        if (!Objects.equals(cn.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Credit note not found: " + uid);
        }
        return cn;
    }

    private CustomerCreditNoteDto hydrateCrediNote(CustomerCreditNote cn) {
        List<CreditNoteAllocationDto> allocationDtos =
            allocations.findByCustomerCreditNoteIdOrderByAllocatedAtAsc(cn.getId())
                .stream()
                .map(a -> new CreditNoteAllocationDto(
                    a.getId(),
                    a.getSalesInvoiceId(),
                    invoices.findById(a.getSalesInvoiceId())
                        .map(SalesInvoice::getNumber).orElse(null),
                    a.getAmount(),
                    a.getAllocatedAt(),
                    users.findById(a.getAllocatedBy())
                        .map(AppUser::getUsername).orElse(null)
                ))
                .toList();
        return CustomerCreditNoteDto.from(cn, allocationDtos);
    }
}

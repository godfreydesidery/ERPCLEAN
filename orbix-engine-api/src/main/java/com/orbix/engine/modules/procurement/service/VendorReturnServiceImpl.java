package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.procurement.domain.dto.ApplyVendorCreditNoteRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.CreateVendorReturnRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.IssueVendorCreditNoteRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.VendorCreditNoteAllocationDto;
import com.orbix.engine.modules.procurement.domain.dto.VendorCreditNoteDto;
import com.orbix.engine.modules.procurement.domain.dto.VendorReturnDto;
import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoice;
import com.orbix.engine.modules.procurement.domain.entity.VendorCreditNote;
import com.orbix.engine.modules.procurement.domain.entity.VendorCreditNoteAllocation;
import com.orbix.engine.modules.procurement.domain.entity.VendorReturn;
import com.orbix.engine.modules.procurement.domain.entity.VendorReturnLine;
import com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus;
import com.orbix.engine.modules.procurement.domain.enums.VendorCreditNoteStatus;
import com.orbix.engine.modules.procurement.domain.event.VendorCreditNoteApplied;
import com.orbix.engine.modules.procurement.domain.event.VendorCreditNoteIssued;
import com.orbix.engine.modules.procurement.domain.event.VendorReturnPosted;
import com.orbix.engine.modules.procurement.repository.SupplierInvoiceRepository;
import com.orbix.engine.modules.procurement.repository.VendorCreditNoteAllocationRepository;
import com.orbix.engine.modules.procurement.repository.VendorCreditNoteRepository;
import com.orbix.engine.modules.procurement.repository.VendorReturnLineRepository;
import com.orbix.engine.modules.procurement.repository.VendorReturnRepository;
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
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VendorReturnServiceImpl implements VendorReturnService {

    private static final int MONEY_SCALE = 4;
    private static final String AGG_RETURN = "VendorReturn";
    private static final String AGG_CN     = "VendorCreditNote";
    private static final String F_RETURN_ID  = "vendorReturnId";
    private static final String F_CN_ID      = "vendorCreditNoteId";
    private static final String F_NUMBER     = "number";
    private static final String F_SUPPLIER_ID = "supplierId";
    private static final String F_TOTAL_AMOUNT = "totalAmount";

    private final VendorReturnRepository returns;
    private final VendorReturnLineRepository lines;
    private final VendorCreditNoteRepository creditNotes;
    private final VendorCreditNoteAllocationRepository allocations;
    private final SupplierInvoiceRepository invoices;
    private final SupplierInvoiceService supplierInvoiceService;
    private final ItemRepository items;
    private final VatGroupRepository vatGroups;
    private final AppUserRepository users;
    private final StockMoveService stockMoveService;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = AGG_RETURN)
    public VendorReturnDto createDraft(CreateVendorReturnRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        branchScope.requireAccess(request.branchId());
        String number = request.number().trim().toUpperCase();
        if (returns.existsByBranchIdAndNumber(request.branchId(), number)) {
            throw new IllegalArgumentException(
                "Vendor return number already exists for this branch: " + number);
        }
        if (request.originalSupplierInvoiceId() != null) {
            SupplierInvoice invoice = invoices.findById(request.originalSupplierInvoiceId())
                .orElseThrow(() -> new NoSuchElementException(
                    "Original supplier invoice not found: " + request.originalSupplierInvoiceId()));
            if (!Objects.equals(invoice.getCompanyId(), companyId)) {
                throw new NoSuchElementException(
                    "Original supplier invoice not found: " + request.originalSupplierInvoiceId());
            }
            if (!Objects.equals(invoice.getSupplierId(), request.supplierId())) {
                throw new IllegalArgumentException(
                    "Original invoice belongs to a different supplier");
            }
        }

        VendorReturn ret = returns.save(new VendorReturn(
            number, companyId, request.branchId(), request.supplierId(),
            request.originalGrnId(), request.originalSupplierInvoiceId(),
            request.returnDate(), request.reason(),
            request.restock(), request.notes(), actorId
        ));
        List<VendorReturnLine> savedLines = saveLinesAndRollUp(ret, request.lines(), companyId);

        events.publish("VendorReturnCreated.v1", AGG_RETURN, String.valueOf(ret.getId()),
            java.util.Map.of(F_RETURN_ID, ret.getId(), F_NUMBER, ret.getNumber(),
                F_SUPPLIER_ID, ret.getSupplierId(),
                F_TOTAL_AMOUNT, ret.getTotalAmount(),
                "reason", ret.getReason().name(),
                "restock", ret.isRestock()));
        return VendorReturnDto.from(ret, savedLines);
    }

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG_RETURN)
    public VendorReturnDto post(String uid) {
        VendorReturn ret = requireReturnByUid(uid);
        Long actorId = context.userId();
        Long companyId = context.companyId();
        List<VendorReturnLine> returnLines = lines.findByVendorReturnIdOrderByLineNoAsc(ret.getId());

        // restock=true  → stock physically goes back to supplier → RETURN_OUT (reduces on-hand)
        // restock=false → goods were damaged/unusable, never in sellable stock → DAMAGE (write-off)
        StockMoveType moveType = ret.isRestock() ? StockMoveType.RETURN_OUT : StockMoveType.DAMAGE;
        for (VendorReturnLine line : returnLines) {
            Item item = requireItem(line.getItemId(), companyId);
            if (item.isBatchTracked()) {
                throw new IllegalArgumentException(
                    "Cannot post a vendor return with batch-tracked items in Slice H.1 (item "
                        + item.getCode() + "); batch-routed returns land in a later slice");
            }
            // RETURN_OUT reduces on-hand (negative qty relative to GRN direction)
            // DAMAGE also reduces on-hand (qty negated to signal deduction)
            BigDecimal qty = line.getReturnedQty().negate();
            stockMoveService.post(new PostStockMoveRequestDto(
                line.getItemId(), ret.getBranchId(),
                qty, line.getUnitPrice(),
                moveType, AGG_RETURN, ret.getId(),
                ret.getReason().name(), false, null
            ));
        }
        ret.post(actorId);
        events.publish(VendorReturnPosted.TYPE, AGG_RETURN, String.valueOf(ret.getId()),
            new VendorReturnPosted(
                ret.getUid(), ret.getSupplierId(), ret.getBranchId(),
                ret.getTotalAmount(), moveType.name(), actorId
            ).toPayload());
        return VendorReturnDto.from(ret, returnLines);
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL", entityType = AGG_RETURN)
    public VendorReturnDto cancel(String uid) {
        VendorReturn ret = requireReturnByUid(uid);
        ret.cancel(context.userId());
        events.publish("VendorReturnCancelled.v1", AGG_RETURN, String.valueOf(ret.getId()),
            java.util.Map.of(F_RETURN_ID, ret.getId(), F_NUMBER, ret.getNumber()));
        return VendorReturnDto.from(ret, lines.findByVendorReturnIdOrderByLineNoAsc(ret.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "ISSUE_CREDIT", entityType = AGG_CN)
    public VendorCreditNoteDto issueCreditNote(String uid, IssueVendorCreditNoteRequestDto request) {
        VendorReturn ret = requireReturnByUid(uid);
        String number = request.number().trim().toUpperCase();
        if (creditNotes.existsByBranchIdAndNumber(ret.getBranchId(), number)) {
            throw new IllegalArgumentException(
                "Credit-note number already exists for this branch: " + number);
        }
        // Use the original invoice's currency when present, else fall back to TZS.
        String currencyCode = ret.getOriginalSupplierInvoiceId() != null
            ? invoices.findById(ret.getOriginalSupplierInvoiceId())
                .map(SupplierInvoice::getCurrencyCode).orElse("TZS")
            : "TZS";
        VendorCreditNote cn = creditNotes.save(new VendorCreditNote(
            number, ret.getCompanyId(), ret.getBranchId(), ret.getSupplierId(),
            ret.getId(), ret.getReturnDate(), currencyCode,
            ret.getTotalAmount(), request.notes(), context.userId()
        ));
        ret.markCredited(context.userId());

        events.publish(VendorCreditNoteIssued.TYPE, AGG_CN, String.valueOf(cn.getId()),
            new VendorCreditNoteIssued(
                cn.getUid(), cn.getSupplierId(), ret.getId(),
                cn.getTotalAmount(), context.userId()
            ).toPayload());
        return VendorCreditNoteDto.from(cn);
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<VendorReturnDto> list(Long branchId, Pageable pageable) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        Page<VendorReturn> page = scope == null
            ? returns.findByCompanyIdOrderByIdDesc(companyId, pageable)
            : returns.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, scope, pageable);
        return PageDto.of(page, r -> VendorReturnDto.from(r, lines.findByVendorReturnIdOrderByLineNoAsc(r.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public VendorReturnDto get(String uid) {
        VendorReturn ret = requireReturnByUid(uid);
        return VendorReturnDto.from(ret, lines.findByVendorReturnIdOrderByLineNoAsc(ret.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VendorCreditNoteDto> listCreditNotes(Long branchId) {
        Long companyId = context.companyId();
        return creditNotes.findByCompanyIdOrderByIdDesc(companyId).stream()
            .filter(c -> branchId == null || Objects.equals(c.getBranchId(), branchId))
            .map(VendorCreditNoteDto::from)
            .toList();
    }

    @Override
    @Transactional
    @Auditable(action = "APPLY_CREDIT", entityType = AGG_CN)
    public VendorCreditNoteDto applyToInvoice(String creditNoteUid, ApplyVendorCreditNoteRequestDto req) {
        Long companyId = context.companyId();
        Long actorId   = context.userId();

        // 1. Load + validate credit note.
        VendorCreditNote cn = requireCreditNoteByUid(creditNoteUid, companyId);
        if (cn.getStatus() != VendorCreditNoteStatus.POSTED
                && cn.getStatus() != VendorCreditNoteStatus.PARTIALLY_ALLOCATED) {
            throw new IllegalStateException(
                "Credit note " + creditNoteUid + " is " + cn.getStatus()
                    + "; only POSTED or PARTIALLY_ALLOCATED notes can be applied");
        }

        // 2. Validate amount <= available.
        BigDecimal available = cn.getTotalAmount().subtract(cn.getAllocatedAmount());
        if (req.amount().compareTo(available) > 0) {
            throw new IllegalArgumentException(
                "Apply amount " + req.amount() + " exceeds available credit " + available
                    + " on credit note " + creditNoteUid);
        }

        // 3. Load + validate target invoice.
        SupplierInvoice invoice = invoices.findByUid(req.supplierInvoiceUid())
            .orElseThrow(() -> new NoSuchElementException(
                "Supplier invoice not found: " + req.supplierInvoiceUid()));
        if (!Objects.equals(invoice.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Supplier invoice not found: " + req.supplierInvoiceUid());
        }
        if (!Objects.equals(invoice.getSupplierId(), cn.getSupplierId())) {
            throw new IllegalArgumentException(
                "Supplier invoice belongs to a different supplier than the credit note");
        }
        if (invoice.getStatus() != SupplierInvoiceStatus.POSTED
                && invoice.getStatus() != SupplierInvoiceStatus.PARTIALLY_PAID) {
            throw new IllegalArgumentException(
                "Supplier invoice " + req.supplierInvoiceUid() + " is " + invoice.getStatus()
                    + "; only POSTED or PARTIALLY_PAID invoices can receive a credit-note apply");
        }
        BigDecimal invoiceOutstanding = invoice.getTotalAmount().subtract(invoice.getPaidAmount());
        if (req.amount().compareTo(invoiceOutstanding) > 0) {
            throw new IllegalArgumentException(
                "Apply amount " + req.amount() + " exceeds invoice outstanding "
                    + invoiceOutstanding + " on invoice " + req.supplierInvoiceUid());
        }

        // 4. Persist allocation row.
        VendorCreditNoteAllocation alloc = VendorCreditNoteAllocation.builder()
            .vendorCreditNoteId(cn.getId())
            .supplierInvoiceId(invoice.getId())
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
            ? VendorCreditNoteStatus.FULLY_ALLOCATED
            : VendorCreditNoteStatus.PARTIALLY_ALLOCATED);

        // 6. Apply credit to invoice in same TX (ADR-0004 exemption #23).
        supplierInvoiceService.applyVendorCredit(invoice.getId(), req.amount());

        // 7. Emit outbox event.
        events.publish(VendorCreditNoteApplied.TYPE, AGG_CN, String.valueOf(cn.getId()),
            new VendorCreditNoteApplied(
                cn.getUid(), req.supplierInvoiceUid(),
                req.amount(), cn.getCurrencyCode(), actorId
            ).toPayload());

        // 8. Return hydrated DTO.
        return hydrateCreditNote(cn);
    }

    // ----- private helpers ---------------------------------------------------

    private List<VendorReturnLine> saveLinesAndRollUp(VendorReturn ret,
                                                      List<CreateVendorReturnRequestDto.LineDto> requestLines,
                                                      Long companyId) {
        List<VendorReturnLine> saved = new ArrayList<>(requestLines.size());
        BigDecimal total = BigDecimal.ZERO;
        int lineNo = 1;
        for (CreateVendorReturnRequestDto.LineDto input : requestLines) {
            Item item = requireItem(input.itemId(), companyId);
            Long uomId = input.uomId() != null ? input.uomId() : item.getUomId();
            Long vatGroupId = input.vatGroupId() != null ? input.vatGroupId() : item.getVatGroupId();
            VatGroup vat = requireVatGroup(vatGroupId, companyId);
            BigDecimal net = input.returnedQty().multiply(input.unitPrice())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal tax = net.multiply(vat.getRate())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal lineTotal = net.add(tax);

            VendorReturnLine line = lines.save(new VendorReturnLine(
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

    private VendorReturn requireReturnByUid(String uid) {
        VendorReturn ret = returns.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Vendor return not found: " + uid));
        if (!Objects.equals(ret.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Vendor return not found: " + uid);
        }
        branchScope.requireAccess(ret.getBranchId());
        return ret;
    }

    private VendorCreditNote requireCreditNoteByUid(String uid, Long companyId) {
        VendorCreditNote cn = creditNotes.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Vendor credit note not found: " + uid));
        if (!Objects.equals(cn.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Vendor credit note not found: " + uid);
        }
        return cn;
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

    private VendorCreditNoteDto hydrateCreditNote(VendorCreditNote cn) {
        List<VendorCreditNoteAllocationDto> allocationDtos =
            allocations.findByVendorCreditNoteIdOrderByAllocatedAtAsc(cn.getId())
                .stream()
                .map(a -> new VendorCreditNoteAllocationDto(
                    a.getId(),
                    a.getSupplierInvoiceId(),
                    invoices.findById(a.getSupplierInvoiceId())
                        .map(SupplierInvoice::getNumber).orElse(null),
                    a.getAmount(),
                    a.getAllocatedAt(),
                    users.findById(a.getAllocatedBy())
                        .map(AppUser::getUsername).orElse(null)
                ))
                .toList();
        return VendorCreditNoteDto.from(cn, allocationDtos);
    }
}

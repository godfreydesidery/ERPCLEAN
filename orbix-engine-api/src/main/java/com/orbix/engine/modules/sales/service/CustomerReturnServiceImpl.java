package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.sales.domain.dto.CreateCustomerReturnRequestDto;
import com.orbix.engine.modules.sales.domain.dto.CustomerCreditNoteDto;
import com.orbix.engine.modules.sales.domain.dto.CustomerReturnDto;
import com.orbix.engine.modules.sales.domain.dto.IssueCreditNoteRequestDto;
import com.orbix.engine.modules.sales.domain.entity.CustomerCreditNote;
import com.orbix.engine.modules.sales.domain.entity.CustomerReturn;
import com.orbix.engine.modules.sales.domain.entity.CustomerReturnLine;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.sales.repository.CustomerCreditNoteRepository;
import com.orbix.engine.modules.sales.repository.CustomerReturnLineRepository;
import com.orbix.engine.modules.sales.repository.CustomerReturnRepository;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.service.StockMoveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private final CustomerReturnRepository returns;
    private final CustomerReturnLineRepository lines;
    private final CustomerCreditNoteRepository creditNotes;
    private final SalesInvoiceRepository invoices;
    private final ItemRepository items;
    private final VatGroupRepository vatGroups;
    private final StockMoveService stockMoveService;
    private final DayGuard dayGuard;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = AGG_RETURN)
    public CustomerReturnDto createDraft(CreateCustomerReturnRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
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
                "customerId", ret.getCustomerId(),
                "totalAmount", ret.getTotalAmount(),
                "reason", ret.getReason().name(),
                "restock", ret.isRestock()));
        return CustomerReturnDto.from(ret, savedLines);
    }

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG_RETURN)
    public CustomerReturnDto post(Long returnId) {
        CustomerReturn ret = requireReturn(returnId);
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
                "customerId", ret.getCustomerId(),
                "branchId", ret.getBranchId(),
                "totalAmount", ret.getTotalAmount(),
                "moveType", moveType.name()));
        return CustomerReturnDto.from(ret, returnLines);
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL", entityType = AGG_RETURN)
    public CustomerReturnDto cancel(Long returnId) {
        CustomerReturn ret = requireReturn(returnId);
        ret.cancel(context.userId());
        events.publish("CustomerReturnCancelled.v1", AGG_RETURN, String.valueOf(ret.getId()),
            Map.of(F_RETURN_ID, ret.getId(), F_NUMBER, ret.getNumber()));
        return CustomerReturnDto.from(ret, lines.findByCustomerReturnIdOrderByLineNoAsc(ret.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "ISSUE_CREDIT", entityType = AGG_CN)
    public CustomerCreditNoteDto issueCreditNote(Long returnId, IssueCreditNoteRequestDto request) {
        CustomerReturn ret = requireReturn(returnId);
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
                "customerId", cn.getCustomerId(),
                F_RETURN_ID, ret.getId(),
                "totalAmount", cn.getTotalAmount()));
        return CustomerCreditNoteDto.from(cn);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerReturnDto> list(Long branchId) {
        Long companyId = context.companyId();
        List<CustomerReturn> rows = branchId == null
            ? returns.findByCompanyIdOrderByIdDesc(companyId)
            : returns.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, branchId);
        return rows.stream()
            .map(r -> CustomerReturnDto.from(r, lines.findByCustomerReturnIdOrderByLineNoAsc(r.getId())))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerReturnDto get(Long returnId) {
        CustomerReturn ret = requireReturn(returnId);
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

    private CustomerReturn requireReturn(Long id) {
        CustomerReturn ret = returns.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Customer return not found: " + id));
        if (!Objects.equals(ret.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Customer return not found: " + id);
        }
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
}

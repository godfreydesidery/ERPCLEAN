package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.cash.service.CashLedgerService;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.sales.domain.dto.CreateSalesReceiptRequestDto;
import com.orbix.engine.modules.sales.domain.dto.SalesReceiptDto;
import com.orbix.engine.modules.sales.domain.entity.ReceiptAllocation;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.sales.domain.entity.SalesReceipt;
import com.orbix.engine.modules.sales.domain.enums.PaymentTerms;
import com.orbix.engine.modules.sales.domain.enums.ReceiptMethod;
import com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus;
import com.orbix.engine.modules.sales.domain.enums.SalesReceiptStatus;
import com.orbix.engine.modules.sales.repository.ReceiptAllocationRepository;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import com.orbix.engine.modules.sales.repository.SalesReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesReceiptServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long CUSTOMER_ID = 540L;
    private static final Long ACTOR_ID = 4L;
    private static final Long INVOICE_A = 6001L;
    private static final Long INVOICE_B = 6002L;

    @Mock private SalesReceiptRepository receipts;
    @Mock private ReceiptAllocationRepository allocations;
    @Mock private SalesInvoiceRepository invoices;
    @Mock private DayGuard dayGuard;
    @Mock private CashLedgerService cashLedger;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;

    @InjectMocks private SalesReceiptServiceImpl service;

    private final AtomicLong nextId = new AtomicLong(7000);

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenReturn(new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 15), ACTOR_ID));
        lenient().when(receipts.save(any(SalesReceipt.class))).thenAnswer(inv -> {
            SalesReceipt r = inv.getArgument(0);
            if (r.getId() == null) r.setId(nextId.getAndIncrement());
            return r;
        });
        lenient().when(allocations.save(any(ReceiptAllocation.class))).thenAnswer(inv -> {
            ReceiptAllocation a = inv.getArgument(0);
            a.setId(nextId.getAndIncrement());
            return a;
        });
    }

    private SalesInvoice postedInvoice(Long id, BigDecimal total, BigDecimal paid) {
        SalesInvoice s = new SalesInvoice("SI-" + id, COMPANY_ID, BRANCH_ID, CUSTOMER_ID, null,
            LocalDate.of(2026, 5, 13), LocalDate.of(2026, 6, 12),
            PaymentTerms.CREDIT, "TZS", 5L, null, null, ACTOR_ID);
        s.setId(id);
        s.rollUpTotals(total, BigDecimal.ZERO, BigDecimal.ZERO);
        s.post(LocalDate.of(2026, 5, 13), ACTOR_ID);
        if (paid.signum() > 0) {
            s.applyReceipt(paid, ACTOR_ID);
        }
        return s;
    }

    private CreateSalesReceiptRequestDto draft(String number, BigDecimal total,
                                               List<CreateSalesReceiptRequestDto.Allocation> allocs) {
        return new CreateSalesReceiptRequestDto(number, BRANCH_ID, CUSTOMER_ID,
            LocalDate.of(2026, 5, 20), ReceiptMethod.BANK_TRANSFER, "TXN-1",
            "TZS", total, "test receipt", allocs);
    }

    @Test
    void createDraft_capturesAllocations_andTracksUnallocated() {
        SalesInvoice invoice = postedInvoice(INVOICE_A, new BigDecimal("1000"), BigDecimal.ZERO);
        when(invoices.findById(INVOICE_A)).thenReturn(Optional.of(invoice));

        SalesReceiptDto dto = service.createDraft(draft("SR-1", new BigDecimal("700"),
            List.of(new CreateSalesReceiptRequestDto.Allocation(INVOICE_A, new BigDecimal("500")))));

        assertThat(dto.totalAmount()).isEqualByComparingTo("700");
        assertThat(dto.allocatedAmount()).isEqualByComparingTo("500");
        assertThat(dto.unallocatedAmount()).isEqualByComparingTo("200");
        // invoice untouched until post
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("0");
        verify(events).publish(eq("SalesReceiptCreated.v1"), any(), any(), any());
    }

    @Test
    void createDraft_allocationExceedsOutstanding_rejected() {
        SalesInvoice invoice = postedInvoice(INVOICE_A, new BigDecimal("1000"), new BigDecimal("700"));
        when(invoices.findById(INVOICE_A)).thenReturn(Optional.of(invoice));

        CreateSalesReceiptRequestDto request = draft("SR-OVR", new BigDecimal("500"),
            List.of(new CreateSalesReceiptRequestDto.Allocation(INVOICE_A, new BigDecimal("500"))));
        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("outstanding");
    }

    @Test
    void createDraft_allocationsExceedReceiptTotal_rejected() {
        SalesInvoice a = postedInvoice(INVOICE_A, new BigDecimal("1000"), BigDecimal.ZERO);
        SalesInvoice b = postedInvoice(INVOICE_B, new BigDecimal("500"), BigDecimal.ZERO);
        when(invoices.findById(INVOICE_A)).thenReturn(Optional.of(a));
        when(invoices.findById(INVOICE_B)).thenReturn(Optional.of(b));

        CreateSalesReceiptRequestDto request = draft("SR-X", new BigDecimal("1200"),
            List.of(
                new CreateSalesReceiptRequestDto.Allocation(INVOICE_A, new BigDecimal("800")),
                new CreateSalesReceiptRequestDto.Allocation(INVOICE_B, new BigDecimal("500"))));
        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds receipt total");
    }

    @Test
    void createDraft_foreignCustomer_rejected() {
        SalesInvoice foreign = new SalesInvoice("SI-FOR", COMPANY_ID, BRANCH_ID, 999L, null,
            LocalDate.now(), null, PaymentTerms.CREDIT, "TZS", 5L,
            null, null, ACTOR_ID);
        foreign.setId(INVOICE_A);
        foreign.rollUpTotals(new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO);
        foreign.post(LocalDate.now(), ACTOR_ID);
        when(invoices.findById(INVOICE_A)).thenReturn(Optional.of(foreign));

        CreateSalesReceiptRequestDto request = draft("SR-FC", new BigDecimal("100"),
            List.of(new CreateSalesReceiptRequestDto.Allocation(INVOICE_A, new BigDecimal("100"))));
        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("different customer");
    }

    @Test
    void post_advancesInvoicePaid_andFlipsPartiallyPaid() {
        SalesReceipt receipt = createdReceipt("SR-P", new BigDecimal("400"));
        when(receipts.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        SalesInvoice invoice = postedInvoice(INVOICE_A, new BigDecimal("1000"), BigDecimal.ZERO);
        when(invoices.findById(INVOICE_A)).thenReturn(Optional.of(invoice));
        when(allocations.findBySalesReceiptId(receipt.getId())).thenReturn(List.of(
            new ReceiptAllocation(receipt.getId(), INVOICE_A, new BigDecimal("400"), ACTOR_ID)));

        SalesReceiptDto dto = service.post(receipt.getId());

        assertThat(dto.status()).isEqualTo(SalesReceiptStatus.POSTED);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("400");
        assertThat(invoice.getStatus()).isEqualTo(SalesInvoiceStatus.PARTIALLY_PAID);
        verify(dayGuard).requireOpenDay(BRANCH_ID);
        verify(events).publish(eq("SalesReceiptPosted.v1"), any(), any(), any());
    }

    @Test
    void post_fullSettlement_flipsInvoiceToPaid() {
        SalesReceipt receipt = createdReceipt("SR-FULL", new BigDecimal("1000"));
        when(receipts.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        SalesInvoice invoice = postedInvoice(INVOICE_A, new BigDecimal("1000"), BigDecimal.ZERO);
        when(invoices.findById(INVOICE_A)).thenReturn(Optional.of(invoice));
        when(allocations.findBySalesReceiptId(receipt.getId())).thenReturn(List.of(
            new ReceiptAllocation(receipt.getId(), INVOICE_A, new BigDecimal("1000"), ACTOR_ID)));

        service.post(receipt.getId());

        assertThat(invoice.getStatus()).isEqualTo(SalesInvoiceStatus.PAID);
    }

    @Test
    void post_dayClosed_isRejected() {
        SalesReceipt receipt = createdReceipt("SR-DAY", new BigDecimal("100"));
        when(receipts.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenThrow(new IllegalStateException("No open business day"));

        Long id = receipt.getId();
        assertThatThrownBy(() -> service.post(id))
            .isInstanceOf(IllegalStateException.class);
        verify(events, never()).publish(eq("SalesReceiptPosted.v1"), any(), any(), any());
    }

    @Test
    void cancel_fromDraft_succeeds() {
        SalesReceipt receipt = createdReceipt("SR-C", new BigDecimal("100"));
        when(receipts.findById(receipt.getId())).thenReturn(Optional.of(receipt));

        SalesReceiptDto dto = service.cancel(receipt.getId());

        assertThat(dto.status()).isEqualTo(SalesReceiptStatus.CANCELLED);
        verify(events).publish(eq("SalesReceiptCancelled.v1"), any(), any(), any());
    }

    @Test
    void createDraft_duplicateBranchNumber_rejected() {
        when(receipts.existsByBranchIdAndNumber(BRANCH_ID, "SR-DUP")).thenReturn(true);

        CreateSalesReceiptRequestDto request = draft("SR-DUP", new BigDecimal("100"), List.of());
        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    private SalesReceipt createdReceipt(String number, BigDecimal total) {
        SalesReceipt r = new SalesReceipt(number, COMPANY_ID, BRANCH_ID, CUSTOMER_ID,
            LocalDate.of(2026, 5, 20), ReceiptMethod.CASH, null, "TZS",
            total, null, ACTOR_ID);
        r.setId(nextId.getAndIncrement());
        return r;
    }
}

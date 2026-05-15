package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.cash.domain.dto.CreateSupplierPaymentRequestDto;
import com.orbix.engine.modules.cash.domain.dto.SupplierPaymentDto;
import com.orbix.engine.modules.cash.domain.entity.SupplierPayment;
import com.orbix.engine.modules.cash.domain.entity.SupplierPaymentAllocation;
import com.orbix.engine.modules.cash.domain.enums.PaymentMethod;
import com.orbix.engine.modules.cash.domain.enums.SupplierPaymentStatus;
import com.orbix.engine.modules.cash.repository.SupplierPaymentAllocationRepository;
import com.orbix.engine.modules.cash.repository.SupplierPaymentRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoice;
import com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus;
import com.orbix.engine.modules.procurement.repository.SupplierInvoiceRepository;
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
class SupplierPaymentServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long SUPPLIER_ID = 808L;
    private static final Long ACTOR_ID = 4L;
    private static final Long INVOICE_A_ID = 3001L;
    private static final Long INVOICE_B_ID = 3002L;

    @Mock private SupplierPaymentRepository payments;
    @Mock private SupplierPaymentAllocationRepository allocations;
    @Mock private SupplierInvoiceRepository invoices;
    @Mock private DayGuard dayGuard;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;

    @InjectMocks private SupplierPaymentServiceImpl service;

    private final AtomicLong nextId = new AtomicLong(4000);

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);

        lenient().when(payments.save(any(SupplierPayment.class))).thenAnswer(inv -> {
            SupplierPayment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(nextId.getAndIncrement());
            return p;
        });
        lenient().when(allocations.save(any(SupplierPaymentAllocation.class))).thenAnswer(inv -> {
            SupplierPaymentAllocation a = inv.getArgument(0);
            a.setId(nextId.getAndIncrement());
            return a;
        });
    }

    private SupplierInvoice postedInvoice(Long id, BigDecimal total, BigDecimal alreadyPaid) {
        SupplierInvoice invoice = new SupplierInvoice("SI-" + id, "INV-" + id, COMPANY_ID,
            BRANCH_ID, SUPPLIER_ID, LocalDate.of(2026, 5, 10), LocalDate.of(2026, 6, 9),
            "TZS", total, BigDecimal.ZERO, null, ACTOR_ID);
        invoice.setId(id);
        invoice.post(ACTOR_ID);
        if (alreadyPaid.signum() > 0) {
            invoice.applyPayment(alreadyPaid, ACTOR_ID);
        }
        return invoice;
    }

    private CreateSupplierPaymentRequestDto draft(String number, BigDecimal total,
                                                  List<CreateSupplierPaymentRequestDto.Allocation> allocs) {
        return new CreateSupplierPaymentRequestDto(
            number, BRANCH_ID, SUPPLIER_ID,
            LocalDate.of(2026, 5, 20), PaymentMethod.BANK_TRANSFER, "TT-9876",
            "TZS", total, "test payment", allocs
        );
    }

    @Test
    void createDraft_capturesAllocations_andEmitsCreatedEvent() {
        SupplierInvoice invoice = postedInvoice(INVOICE_A_ID, new BigDecimal("1000"), BigDecimal.ZERO);
        when(invoices.findById(INVOICE_A_ID)).thenReturn(Optional.of(invoice));

        SupplierPaymentDto dto = service.createDraft(draft("SP-1", new BigDecimal("1000"),
            List.of(new CreateSupplierPaymentRequestDto.Allocation(INVOICE_A_ID, new BigDecimal("1000")))));

        assertThat(dto.status()).isEqualTo(SupplierPaymentStatus.DRAFT);
        assertThat(dto.totalAmount()).isEqualByComparingTo("1000");
        assertThat(dto.allocatedAmount()).isEqualByComparingTo("1000");
        assertThat(dto.allocations()).hasSize(1);
        verify(events).publish(eq("SupplierPaymentCreated.v1"), any(), any(), any());
        // The invoice is unchanged until post.
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("0");
        assertThat(invoice.getStatus()).isEqualTo(SupplierInvoiceStatus.POSTED);
    }

    @Test
    void createDraft_allocationExceedsOutstanding_isRejected() {
        SupplierInvoice invoice = postedInvoice(INVOICE_A_ID, new BigDecimal("1000"), new BigDecimal("600"));
        when(invoices.findById(INVOICE_A_ID)).thenReturn(Optional.of(invoice));

        CreateSupplierPaymentRequestDto request = draft("SP-OVR", new BigDecimal("500"),
            List.of(new CreateSupplierPaymentRequestDto.Allocation(INVOICE_A_ID, new BigDecimal("500"))));

        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("outstanding");
        verify(payments, never()).save(any());
    }

    @Test
    void createDraft_allocationsExceedPaymentTotal_isRejected() {
        SupplierInvoice a = postedInvoice(INVOICE_A_ID, new BigDecimal("1000"), BigDecimal.ZERO);
        SupplierInvoice b = postedInvoice(INVOICE_B_ID, new BigDecimal("500"), BigDecimal.ZERO);
        when(invoices.findById(INVOICE_A_ID)).thenReturn(Optional.of(a));
        when(invoices.findById(INVOICE_B_ID)).thenReturn(Optional.of(b));

        CreateSupplierPaymentRequestDto request = draft("SP-X", new BigDecimal("1200"),
            List.of(
                new CreateSupplierPaymentRequestDto.Allocation(INVOICE_A_ID, new BigDecimal("1000")),
                new CreateSupplierPaymentRequestDto.Allocation(INVOICE_B_ID, new BigDecimal("500"))));

        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds payment total");
    }

    @Test
    void createDraft_foreignSupplierInvoice_isRejected() {
        SupplierInvoice foreign = new SupplierInvoice("SI-FOR", "INV-FOR", COMPANY_ID,
            BRANCH_ID, 999L, LocalDate.now(), LocalDate.now().plusDays(30),
            "TZS", new BigDecimal("100"), BigDecimal.ZERO, null, ACTOR_ID);
        foreign.setId(INVOICE_A_ID);
        foreign.post(ACTOR_ID);
        when(invoices.findById(INVOICE_A_ID)).thenReturn(Optional.of(foreign));

        CreateSupplierPaymentRequestDto request = draft("SP-FS", new BigDecimal("100"),
            List.of(new CreateSupplierPaymentRequestDto.Allocation(INVOICE_A_ID, new BigDecimal("100"))));

        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("different supplier");
    }

    @Test
    void createDraft_duplicateInvoiceAllocation_rejected() {
        SupplierInvoice invoice = postedInvoice(INVOICE_A_ID, new BigDecimal("1000"), BigDecimal.ZERO);
        when(invoices.findById(INVOICE_A_ID)).thenReturn(Optional.of(invoice));

        CreateSupplierPaymentRequestDto request = draft("SP-DUP", new BigDecimal("600"),
            List.of(
                new CreateSupplierPaymentRequestDto.Allocation(INVOICE_A_ID, new BigDecimal("400")),
                new CreateSupplierPaymentRequestDto.Allocation(INVOICE_A_ID, new BigDecimal("200"))));

        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duplicate allocation");
    }

    @Test
    void post_advancesInvoicePaidAmount_andEmitsPosted() {
        SupplierPayment payment = createdPayment("SP-P", new BigDecimal("400"));
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        SupplierInvoice invoice = postedInvoice(INVOICE_A_ID, new BigDecimal("1000"), BigDecimal.ZERO);
        when(invoices.findById(INVOICE_A_ID)).thenReturn(Optional.of(invoice));
        when(allocations.findBySupplierPaymentId(payment.getId())).thenReturn(List.of(
            new SupplierPaymentAllocation(payment.getId(), INVOICE_A_ID, new BigDecimal("400"))));

        SupplierPaymentDto dto = service.post(payment.getId());

        assertThat(dto.status()).isEqualTo(SupplierPaymentStatus.POSTED);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("400");
        assertThat(invoice.getStatus()).isEqualTo(SupplierInvoiceStatus.PARTIALLY_PAID);
        verify(dayGuard).requireOpenDay(BRANCH_ID);
        verify(events).publish(eq("SupplierPaymentPosted.v1"), any(), any(), any());
    }

    @Test
    void post_fullSettlement_flipsInvoiceToPaid() {
        SupplierPayment payment = createdPayment("SP-FULL", new BigDecimal("1000"));
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        SupplierInvoice invoice = postedInvoice(INVOICE_A_ID, new BigDecimal("1000"), BigDecimal.ZERO);
        when(invoices.findById(INVOICE_A_ID)).thenReturn(Optional.of(invoice));
        when(allocations.findBySupplierPaymentId(payment.getId())).thenReturn(List.of(
            new SupplierPaymentAllocation(payment.getId(), INVOICE_A_ID, new BigDecimal("1000"))));

        service.post(payment.getId());

        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("1000");
        assertThat(invoice.getStatus()).isEqualTo(SupplierInvoiceStatus.PAID);
    }

    @Test
    void post_dayClosed_isRejected() {
        SupplierPayment payment = createdPayment("SP-DAY", new BigDecimal("100"));
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenThrow(new IllegalStateException("No open business day"));

        Long id = payment.getId();
        assertThatThrownBy(() -> service.post(id))
            .isInstanceOf(IllegalStateException.class);
        verify(events, never()).publish(eq("SupplierPaymentPosted.v1"), any(), any(), any());
    }

    @Test
    void cancel_fromDraft_succeeds() {
        SupplierPayment payment = createdPayment("SP-C", new BigDecimal("100"));
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));

        SupplierPaymentDto dto = service.cancel(payment.getId());

        assertThat(dto.status()).isEqualTo(SupplierPaymentStatus.CANCELLED);
        verify(events).publish(eq("SupplierPaymentCancelled.v1"), any(), any(), any());
    }

    @Test
    void createDraft_duplicateBranchNumber_rejected() {
        when(payments.existsByBranchIdAndNumber(BRANCH_ID, "SP-DUP")).thenReturn(true);

        CreateSupplierPaymentRequestDto request = draft("SP-DUP", new BigDecimal("100"),
            List.of(new CreateSupplierPaymentRequestDto.Allocation(INVOICE_A_ID, new BigDecimal("100"))));

        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    private SupplierPayment createdPayment(String number, BigDecimal total) {
        SupplierPayment payment = new SupplierPayment(number, COMPANY_ID, BRANCH_ID, SUPPLIER_ID,
            LocalDate.of(2026, 5, 20), PaymentMethod.BANK_TRANSFER, "TT-1",
            "TZS", total, null, ACTOR_ID);
        payment.setId(nextId.getAndIncrement());
        return payment;
    }
}

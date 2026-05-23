package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.SettingsService;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.party.domain.entity.Supplier;
import com.orbix.engine.modules.party.repository.SupplierRepository;
import com.orbix.engine.modules.procurement.domain.dto.CreateSupplierInvoiceRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.SupplierInvoiceDto;
import com.orbix.engine.modules.procurement.domain.entity.Grn;
import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoice;
import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoiceGrn;
import com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus;
import com.orbix.engine.modules.procurement.repository.GrnRepository;
import com.orbix.engine.modules.procurement.repository.SupplierInvoiceGrnRepository;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierInvoiceServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long SUPPLIER_ID = 808L;
    private static final Long ACTOR_ID = 4L;
    private static final Long GRN_A_ID = 901L;
    private static final Long GRN_B_ID = 902L;

    @Mock private SupplierInvoiceRepository invoices;
    @Mock private SupplierInvoiceGrnRepository allocations;
    @Mock private GrnRepository grns;
    @Mock private SupplierRepository suppliers;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;
    @Mock private SettingsService settings;

    @InjectMocks private SupplierInvoiceServiceImpl service;

    private final AtomicLong nextId = new AtomicLong(2000);

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(settings.getDecimal(SettingKey.PROCUREMENT_INVOICE_MATCH_TOLERANCE))
            .thenReturn(new BigDecimal("0.005"));

        Supplier supplier = new Supplier(SUPPLIER_ID);
        supplier.setPaymentTermsDays(30);
        lenient().when(suppliers.findById(SUPPLIER_ID)).thenReturn(Optional.of(supplier));

        lenient().when(grns.findById(GRN_A_ID)).thenReturn(Optional.of(grnRow(GRN_A_ID, new BigDecimal("1180"))));
        lenient().when(grns.findById(GRN_B_ID)).thenReturn(Optional.of(grnRow(GRN_B_ID, new BigDecimal("590"))));
        lenient().when(allocations.sumAllocatedToGrn(any(), any())).thenReturn(BigDecimal.ZERO);
        lenient().when(allocations.sumAllocatedToGrn(any(), isNull())).thenReturn(BigDecimal.ZERO);

        lenient().when(invoices.save(any(SupplierInvoice.class))).thenAnswer(inv -> {
            SupplierInvoice s = inv.getArgument(0);
            if (s.getId() == null) s.setId(nextId.getAndIncrement());
            return s;
        });
        lenient().when(allocations.save(any(SupplierInvoiceGrn.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Grn grnRow(Long id, BigDecimal total) {
        Grn grn = new Grn("GRN-" + id, COMPANY_ID, BRANCH_ID, SUPPLIER_ID, null,
            LocalDate.of(2026, 5, 1), null, null, ACTOR_ID);
        grn.setId(id);
        grn.rollUpTotals(total, BigDecimal.ZERO);  // subtotal-only is enough for the over-allocation guard
        grn.post(ACTOR_ID);
        return grn;
    }

    private CreateSupplierInvoiceRequestDto draft(String number, BigDecimal subtotal, BigDecimal tax,
                                                  List<CreateSupplierInvoiceRequestDto.Allocation> allocs) {
        return new CreateSupplierInvoiceRequestDto(
            number, "INV-" + number, BRANCH_ID, SUPPLIER_ID,
            LocalDate.of(2026, 5, 10), null, "TZS",
            subtotal, tax, "test", allocs
        );
    }

    @Test
    void createDraft_singleGrnFullMatch_setsDueDateFromSupplierTerms() {
        CreateSupplierInvoiceRequestDto request = draft("SI-1",
            new BigDecimal("1000"), new BigDecimal("180"),
            List.of(new CreateSupplierInvoiceRequestDto.Allocation(GRN_A_ID, new BigDecimal("1180"))));

        SupplierInvoiceDto dto = service.createDraft(request);

        assertThat(dto.status()).isEqualTo(SupplierInvoiceStatus.DRAFT);
        assertThat(dto.totalAmount()).isEqualByComparingTo("1180");
        // invoiceDate = 2026-05-10, paymentTermsDays = 30 → 2026-06-09
        assertThat(dto.dueDate()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(dto.allocations()).hasSize(1);
        verify(events).publish(eq("SupplierInvoiceCreated.v1"), any(), any(), any());
    }

    @Test
    void createDraft_multiGrn_summedTotalsMatchWithinTolerance() {
        CreateSupplierInvoiceRequestDto request = draft("SI-2",
            new BigDecimal("1500"), new BigDecimal("270"),
            List.of(
                new CreateSupplierInvoiceRequestDto.Allocation(GRN_A_ID, new BigDecimal("1180")),
                new CreateSupplierInvoiceRequestDto.Allocation(GRN_B_ID, new BigDecimal("590"))
            ));

        SupplierInvoiceDto dto = service.createDraft(request);

        assertThat(dto.totalAmount()).isEqualByComparingTo("1770");
        assertThat(dto.allocations()).hasSize(2);
    }

    @Test
    void createDraft_outsideTolerance_isRejected() {
        // Invoice 1180; allocations sum 1100 → diff 80, allowed 1180 * 0.005 = 5.9 → reject
        CreateSupplierInvoiceRequestDto request = draft("SI-OFF",
            new BigDecimal("1000"), new BigDecimal("180"),
            List.of(new CreateSupplierInvoiceRequestDto.Allocation(GRN_A_ID, new BigDecimal("1100"))));

        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tolerance");
        verify(invoices, never()).save(any());
    }

    @Test
    void createDraft_overAllocatingAGrn_isRejected() {
        // GRN A total 1180; pretend 1000 already allocated by other invoices.
        when(allocations.sumAllocatedToGrn(eq(GRN_A_ID), isNull())).thenReturn(new BigDecimal("1000"));

        CreateSupplierInvoiceRequestDto request = draft("SI-OVR",
            new BigDecimal("1000"), new BigDecimal("180"),
            List.of(new CreateSupplierInvoiceRequestDto.Allocation(GRN_A_ID, new BigDecimal("1180"))));

        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Over-allocation");
        verify(invoices, never()).save(any());
    }

    @Test
    void createDraft_grnFromAnotherSupplier_isRejected() {
        Grn foreignSupplier = new Grn("GRN-FOR", COMPANY_ID, BRANCH_ID, 999L, null,
            LocalDate.of(2026, 5, 1), null, null, ACTOR_ID);
        foreignSupplier.setId(910L);
        foreignSupplier.rollUpTotals(new BigDecimal("100"), BigDecimal.ZERO);
        foreignSupplier.post(ACTOR_ID);
        when(grns.findById(910L)).thenReturn(Optional.of(foreignSupplier));

        CreateSupplierInvoiceRequestDto request = draft("SI-X",
            new BigDecimal("85"), new BigDecimal("15"),
            List.of(new CreateSupplierInvoiceRequestDto.Allocation(910L, new BigDecimal("100"))));

        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("different supplier");
    }

    @Test
    void createDraft_unpostedGrn_isRejected() {
        Grn draftGrn = new Grn("GRN-D", COMPANY_ID, BRANCH_ID, SUPPLIER_ID, null,
            LocalDate.of(2026, 5, 1), null, null, ACTOR_ID);
        draftGrn.setId(920L);
        draftGrn.rollUpTotals(new BigDecimal("100"), BigDecimal.ZERO);
        when(grns.findById(920L)).thenReturn(Optional.of(draftGrn));

        CreateSupplierInvoiceRequestDto request = draft("SI-DR",
            new BigDecimal("85"), new BigDecimal("15"),
            List.of(new CreateSupplierInvoiceRequestDto.Allocation(920L, new BigDecimal("100"))));

        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("POSTED");
    }

    @Test
    void createDraft_explicitDueDateOverridesSupplierTerms() {
        CreateSupplierInvoiceRequestDto request = new CreateSupplierInvoiceRequestDto(
            "SI-DD", "INV-SI-DD", BRANCH_ID, SUPPLIER_ID,
            LocalDate.of(2026, 5, 10),
            LocalDate.of(2026, 7, 1),  // explicit due date
            "TZS",
            new BigDecimal("1000"), new BigDecimal("180"), null,
            List.of(new CreateSupplierInvoiceRequestDto.Allocation(GRN_A_ID, new BigDecimal("1180")))
        );

        SupplierInvoiceDto dto = service.createDraft(request);

        assertThat(dto.dueDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void post_emitsMatchedEvent_andFlipsStatus() {
        SupplierInvoice invoice = createdInvoice("SI-P", new BigDecimal("1180"));
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(allocations.findBySupplierInvoiceId(invoice.getId())).thenReturn(List.of(
            new SupplierInvoiceGrn(invoice.getId(), GRN_A_ID, new BigDecimal("1180"))));

        SupplierInvoiceDto dto = service.post(invoice.getId());

        assertThat(dto.status()).isEqualTo(SupplierInvoiceStatus.POSTED);
        verify(events).publish(eq("SupplierInvoiceMatched.v1"), any(), any(), any());
    }

    @Test
    void post_rejectsWhenAllocationsDriftOutsideTolerance() {
        // Header totalAmount 1180, allocations sum to 1100 → outside tolerance.
        SupplierInvoice invoice = createdInvoice("SI-PX", new BigDecimal("1180"));
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(allocations.findBySupplierInvoiceId(invoice.getId())).thenReturn(List.of(
            new SupplierInvoiceGrn(invoice.getId(), GRN_A_ID, new BigDecimal("1100"))));

        Long id = invoice.getId();
        assertThatThrownBy(() -> service.post(id))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tolerance");
    }

    @Test
    void cancel_fromDraft_succeeds() {
        SupplierInvoice invoice = createdInvoice("SI-C", new BigDecimal("100"));
        when(invoices.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        SupplierInvoiceDto dto = service.cancel(invoice.getId());

        assertThat(dto.status()).isEqualTo(SupplierInvoiceStatus.CANCELLED);
        verify(events).publish(eq("SupplierInvoiceCancelled.v1"), any(), any(), any());
    }

    @Test
    void createDraft_duplicateBranchNumber_rejected() {
        when(invoices.existsByBranchIdAndNumber(BRANCH_ID, "SI-DUP")).thenReturn(true);

        CreateSupplierInvoiceRequestDto request = draft("SI-DUP",
            new BigDecimal("1000"), new BigDecimal("180"),
            List.of(new CreateSupplierInvoiceRequestDto.Allocation(GRN_A_ID, new BigDecimal("1180"))));

        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void createDraft_duplicateSupplierInvoiceNo_rejected() {
        when(invoices.existsBySupplierIdAndSupplierInvoiceNo(SUPPLIER_ID, "INV-SI-DUP2"))
            .thenReturn(true);

        CreateSupplierInvoiceRequestDto request = draft("SI-DUP2",
            new BigDecimal("1000"), new BigDecimal("180"),
            List.of(new CreateSupplierInvoiceRequestDto.Allocation(GRN_A_ID, new BigDecimal("1180"))));

        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already on file");
    }

    @Test
    void get_foreignCompany_throwsNotFound() {
        SupplierInvoice foreign = new SupplierInvoice("SI-X", "INV-X", 999L, BRANCH_ID, SUPPLIER_ID,
            LocalDate.now(), LocalDate.now().plusDays(30), "TZS",
            new BigDecimal("100"), BigDecimal.ZERO, null, ACTOR_ID);
        foreign.setId(900L);
        when(invoices.findById(900L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.get(900L)).isInstanceOf(NoSuchElementException.class);
    }

    private SupplierInvoice createdInvoice(String number, BigDecimal total) {
        BigDecimal subtotal = total;
        SupplierInvoice invoice = new SupplierInvoice(number, "INV-" + number, COMPANY_ID,
            BRANCH_ID, SUPPLIER_ID,
            LocalDate.of(2026, 5, 10), LocalDate.of(2026, 6, 9), "TZS",
            subtotal, BigDecimal.ZERO, null, ACTOR_ID);
        invoice.setId(nextId.getAndIncrement());
        return invoice;
    }
}

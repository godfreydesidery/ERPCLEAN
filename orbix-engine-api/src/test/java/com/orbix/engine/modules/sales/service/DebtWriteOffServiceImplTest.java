package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import com.orbix.engine.modules.party.repository.PartyRepository;
import com.orbix.engine.modules.procurement.domain.dto.SupplierInvoiceDto;
import com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus;
import com.orbix.engine.modules.procurement.service.SupplierInvoiceService;
import com.orbix.engine.modules.sales.domain.dto.CreateDebtWriteOffRequestDto;
import com.orbix.engine.modules.sales.domain.dto.DebtWriteOffDto;
import com.orbix.engine.modules.sales.domain.dto.RejectDebtWriteOffRequestDto;
import com.orbix.engine.modules.sales.domain.dto.SalesInvoiceDto;
import com.orbix.engine.modules.sales.domain.entity.DebtWriteOff;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffStatus;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffTargetKind;
import com.orbix.engine.modules.sales.domain.enums.PaymentTerms;
import com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus;
import com.orbix.engine.modules.sales.repository.DebtWriteOffRepository;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebtWriteOffServiceImplTest {

    private static final Long COMPANY_ID   = 7L;
    private static final Long BRANCH_ID    = 12L;
    private static final Long REQUESTER_ID = 4L;
    private static final Long APPROVER_ID  = 9L;
    private static final Long INVOICE_ID   = 500L;
    private static final Long CUSTOMER_ID  = 77L;
    private static final Long SUPPLIER_ID  = 88L;

    private static final BigDecimal THRESHOLD       = new BigDecimal("100000");
    private static final BigDecimal BELOW_THRESHOLD = new BigDecimal("50000");
    private static final BigDecimal ABOVE_THRESHOLD = new BigDecimal("200000");

    @Mock private DebtWriteOffRepository writeOffs;
    @Mock private SalesInvoiceRepository salesInvoices;
    @Mock private SalesInvoiceService salesInvoiceService;
    @Mock private SupplierInvoiceService supplierInvoiceService;
    @Mock private PartyRepository parties;
    @Mock private AppUserRepository users;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;

    @InjectMocks private DebtWriteOffServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "dualApprovalThreshold", THRESHOLD);
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.requireBranchId()).thenReturn(BRANCH_ID);
        lenient().when(context.userId()).thenReturn(REQUESTER_ID);
        lenient().when(parties.findById(any())).thenReturn(Optional.empty());
        lenient().when(users.findById(any())).thenReturn(Optional.empty());
    }

    // ------------------------------------------------------------------
    // create() — AR invoice, amount ≤ threshold, caller has APPROVE perm
    // → auto-post (POSTED immediately)
    // ------------------------------------------------------------------

    @Test
    void create_arInvoice_belowThreshold_withApprovePerm_autoPostsImmediately() {
        setSecurityWithApprove();
        SalesInvoiceDto inv = arInvoice(INVOICE_ID, new BigDecimal("100000"), BigDecimal.ZERO,
            SalesInvoiceStatus.POSTED);
        when(salesInvoiceService.get(inv.uid())).thenReturn(inv);
        doNothing().when(salesInvoiceService).applyWriteOff(eq(INVOICE_ID), any());

        DebtWriteOff saved = buildWriteOff(BELOW_THRESHOLD, DebtWriteOffStatus.POSTED);
        when(writeOffs.save(any())).thenReturn(saved);

        CreateDebtWriteOffRequestDto req = new CreateDebtWriteOffRequestDto(
            DebtWriteOffTargetKind.CUSTOMER_INVOICE, inv.uid(), BELOW_THRESHOLD, "Bad debt");

        DebtWriteOffDto dto = service.create(req);

        assertThat(dto.status()).isEqualTo(DebtWriteOffStatus.POSTED);
        verify(salesInvoiceService).applyWriteOff(INVOICE_ID, BELOW_THRESHOLD);
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(events).publish(typeCaptor.capture(), any(), any(), any());
        assertThat(typeCaptor.getValue()).isEqualTo("DebtWriteOffPosted.v1");
    }

    // ------------------------------------------------------------------
    // create() — amount > threshold → PENDING_APPROVAL regardless of perm
    // ------------------------------------------------------------------

    @Test
    void create_arInvoice_aboveThreshold_createsPendingApproval() {
        setSecurityWithApprove();
        SalesInvoiceDto inv = arInvoice(INVOICE_ID, new BigDecimal("300000"), BigDecimal.ZERO,
            SalesInvoiceStatus.POSTED);
        when(salesInvoiceService.get(inv.uid())).thenReturn(inv);

        DebtWriteOff saved = buildWriteOff(ABOVE_THRESHOLD, DebtWriteOffStatus.PENDING_APPROVAL);
        when(writeOffs.save(any())).thenReturn(saved);

        CreateDebtWriteOffRequestDto req = new CreateDebtWriteOffRequestDto(
            DebtWriteOffTargetKind.CUSTOMER_INVOICE, inv.uid(), ABOVE_THRESHOLD, "Big debt");

        DebtWriteOffDto dto = service.create(req);

        assertThat(dto.status()).isEqualTo(DebtWriteOffStatus.PENDING_APPROVAL);
        verify(salesInvoiceService, never()).applyWriteOff(any(), any());
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(events).publish(typeCaptor.capture(), any(), any(), any());
        assertThat(typeCaptor.getValue()).isEqualTo("DebtWriteOffRequested.v1");
    }

    // ------------------------------------------------------------------
    // create() — caller lacks APPROVE perm → PENDING_APPROVAL even if ≤ threshold
    // ------------------------------------------------------------------

    @Test
    void create_belowThreshold_withoutApprovePerm_createsPendingApproval() {
        setSecurityWithoutApprove();
        SalesInvoiceDto inv = arInvoice(INVOICE_ID, new BigDecimal("100000"), BigDecimal.ZERO,
            SalesInvoiceStatus.POSTED);
        when(salesInvoiceService.get(inv.uid())).thenReturn(inv);

        DebtWriteOff saved = buildWriteOff(BELOW_THRESHOLD, DebtWriteOffStatus.PENDING_APPROVAL);
        when(writeOffs.save(any())).thenReturn(saved);

        CreateDebtWriteOffRequestDto req = new CreateDebtWriteOffRequestDto(
            DebtWriteOffTargetKind.CUSTOMER_INVOICE, inv.uid(), BELOW_THRESHOLD, "reason");

        DebtWriteOffDto dto = service.create(req);

        assertThat(dto.status()).isEqualTo(DebtWriteOffStatus.PENDING_APPROVAL);
        verify(salesInvoiceService, never()).applyWriteOff(any(), any());
    }

    // ------------------------------------------------------------------
    // create() — amount validation
    // ------------------------------------------------------------------

    @Test
    void create_amountExceedsOutstanding_throwsIllegalArgument() {
        setSecurityWithApprove();
        // invoice outstanding = 30000, request amount = 50000
        SalesInvoiceDto inv = arInvoice(INVOICE_ID, new BigDecimal("100000"),
            new BigDecimal("70000"), SalesInvoiceStatus.PARTIALLY_PAID);
        when(salesInvoiceService.get(inv.uid())).thenReturn(inv);

        CreateDebtWriteOffRequestDto req = new CreateDebtWriteOffRequestDto(
            DebtWriteOffTargetKind.CUSTOMER_INVOICE, inv.uid(), new BigDecimal("50000"), "reason");

        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds outstanding");
    }

    @Test
    void create_invalidStatus_throwsIllegalArgument() {
        setSecurityWithApprove();
        SalesInvoiceDto inv = arInvoice(INVOICE_ID, new BigDecimal("100000"), BigDecimal.ZERO,
            SalesInvoiceStatus.DRAFT);
        when(salesInvoiceService.get(inv.uid())).thenReturn(inv);

        CreateDebtWriteOffRequestDto req = new CreateDebtWriteOffRequestDto(
            DebtWriteOffTargetKind.CUSTOMER_INVOICE, inv.uid(), BELOW_THRESHOLD, "reason");

        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("POSTED or PARTIALLY_PAID");
    }

    // ------------------------------------------------------------------
    // approve() — happy path, different user
    // ------------------------------------------------------------------

    @Test
    void approve_pendingApproval_aboveThreshold_differentUser_posts() {
        when(context.userId()).thenReturn(APPROVER_ID);
        DebtWriteOff wo = buildWriteOff(ABOVE_THRESHOLD, DebtWriteOffStatus.PENDING_APPROVAL);
        wo.setRequestedByUserId(REQUESTER_ID);  // different from APPROVER_ID
        String uid = wo.getUid();
        when(writeOffs.findByUid(uid)).thenReturn(Optional.of(wo));
        doNothing().when(salesInvoiceService).applyWriteOff(any(), any());

        DebtWriteOffDto dto = service.approve(uid);

        assertThat(dto.status()).isEqualTo(DebtWriteOffStatus.POSTED);
        verify(salesInvoiceService).applyWriteOff(wo.getTargetInvoiceId(), ABOVE_THRESHOLD);
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(events).publish(typeCaptor.capture(), any(), any(), any());
        assertThat(typeCaptor.getValue()).isEqualTo("DebtWriteOffPosted.v1");
    }

    // ------------------------------------------------------------------
    // approve() — same user above threshold → throws
    // ------------------------------------------------------------------

    @Test
    void approve_aboveThreshold_sameUser_throwsIllegalState() {
        when(context.userId()).thenReturn(REQUESTER_ID);
        DebtWriteOff wo = buildWriteOff(ABOVE_THRESHOLD, DebtWriteOffStatus.PENDING_APPROVAL);
        wo.setRequestedByUserId(REQUESTER_ID);  // same user
        when(writeOffs.findByUid(wo.getUid())).thenReturn(Optional.of(wo));

        assertThatThrownBy(() -> service.approve(wo.getUid()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("dual-approval threshold");
    }

    // ------------------------------------------------------------------
    // approve() — wrong state → throws
    // ------------------------------------------------------------------

    @Test
    void approve_alreadyPosted_throwsIllegalState() {
        when(context.userId()).thenReturn(APPROVER_ID);
        DebtWriteOff wo = buildWriteOff(BELOW_THRESHOLD, DebtWriteOffStatus.POSTED);
        when(writeOffs.findByUid(wo.getUid())).thenReturn(Optional.of(wo));

        assertThatThrownBy(() -> service.approve(wo.getUid()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("POSTED");
    }

    // ------------------------------------------------------------------
    // reject() — happy path
    // ------------------------------------------------------------------

    @Test
    void reject_pendingApproval_differentUser_rejects() {
        when(context.userId()).thenReturn(APPROVER_ID);
        DebtWriteOff wo = buildWriteOff(ABOVE_THRESHOLD, DebtWriteOffStatus.PENDING_APPROVAL);
        wo.setRequestedByUserId(REQUESTER_ID);
        when(writeOffs.findByUid(wo.getUid())).thenReturn(Optional.of(wo));

        RejectDebtWriteOffRequestDto req = new RejectDebtWriteOffRequestDto("Not valid");

        DebtWriteOffDto dto = service.reject(wo.getUid(), req);

        assertThat(dto.status()).isEqualTo(DebtWriteOffStatus.REJECTED);
        verify(salesInvoiceService, never()).applyWriteOff(any(), any());
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(events).publish(typeCaptor.capture(), any(), any(), any());
        assertThat(typeCaptor.getValue()).isEqualTo("DebtWriteOffRejected.v1");
    }

    // ------------------------------------------------------------------
    // reject() — same user → throws (always dual-control for rejection)
    // ------------------------------------------------------------------

    @Test
    void reject_sameUser_throwsIllegalState() {
        when(context.userId()).thenReturn(REQUESTER_ID);
        DebtWriteOff wo = buildWriteOff(BELOW_THRESHOLD, DebtWriteOffStatus.PENDING_APPROVAL);
        wo.setRequestedByUserId(REQUESTER_ID);
        when(writeOffs.findByUid(wo.getUid())).thenReturn(Optional.of(wo));

        assertThatThrownBy(() -> service.reject(wo.getUid(), new RejectDebtWriteOffRequestDto("reason")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("dual-control");
    }

    // ------------------------------------------------------------------
    // reject() — wrong state → throws
    // ------------------------------------------------------------------

    @Test
    void reject_alreadyRejected_throwsIllegalState() {
        when(context.userId()).thenReturn(APPROVER_ID);
        DebtWriteOff wo = buildWriteOff(BELOW_THRESHOLD, DebtWriteOffStatus.REJECTED);
        when(writeOffs.findByUid(wo.getUid())).thenReturn(Optional.of(wo));

        assertThatThrownBy(() -> service.reject(wo.getUid(), new RejectDebtWriteOffRequestDto("reason")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("REJECTED");
    }

    // ------------------------------------------------------------------
    // list() + filter combinations
    // ------------------------------------------------------------------

    @Test
    void list_noFilter_returnsPage() {
        DebtWriteOff wo = buildWriteOff(BELOW_THRESHOLD, DebtWriteOffStatus.POSTED);
        Page<DebtWriteOff> page = new PageImpl<>(List.of(wo));
        when(writeOffs.findFiltered(eq(COMPANY_ID), eq(null), eq(null), any())).thenReturn(page);
        when(salesInvoices.findById(wo.getTargetInvoiceId())).thenReturn(Optional.empty());

        PageDto<DebtWriteOffDto> result = service.list(null, null, PageRequest.of(0, 25));

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void list_statusFilter_delegatesToRepository() {
        when(writeOffs.findFiltered(eq(COMPANY_ID), eq(DebtWriteOffStatus.PENDING_APPROVAL),
            eq(null), any())).thenReturn(Page.empty());

        service.list(DebtWriteOffStatus.PENDING_APPROVAL, null, PageRequest.of(0, 25));

        verify(writeOffs).findFiltered(COMPANY_ID, DebtWriteOffStatus.PENDING_APPROVAL, null, PageRequest.of(0, 25));
    }

    // ------------------------------------------------------------------
    // Tenant guard — cross-tenant uid → NoSuchElementException
    // ------------------------------------------------------------------

    @Test
    void get_crossTenantUid_throwsNoSuchElement() {
        DebtWriteOff wo = buildWriteOff(BELOW_THRESHOLD, DebtWriteOffStatus.POSTED);
        wo.setCompanyId(999L); // different company
        when(writeOffs.findByUid(wo.getUid())).thenReturn(Optional.of(wo));

        assertThatThrownBy(() -> service.get(wo.getUid()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void approve_crossTenantUid_throwsNoSuchElement() {
        DebtWriteOff wo = buildWriteOff(BELOW_THRESHOLD, DebtWriteOffStatus.PENDING_APPROVAL);
        wo.setCompanyId(999L);
        when(writeOffs.findByUid(wo.getUid())).thenReturn(Optional.of(wo));

        assertThatThrownBy(() -> service.approve(wo.getUid()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void reject_crossTenantUid_throwsNoSuchElement() {
        DebtWriteOff wo = buildWriteOff(BELOW_THRESHOLD, DebtWriteOffStatus.PENDING_APPROVAL);
        wo.setCompanyId(999L);
        when(writeOffs.findByUid(wo.getUid())).thenReturn(Optional.of(wo));

        assertThatThrownBy(() -> service.reject(wo.getUid(), new RejectDebtWriteOffRequestDto("x")))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ------------------------------------------------------------------
    // AP invoice path — supplier invoice write-off
    // ------------------------------------------------------------------

    @Test
    void create_apInvoice_aboveThreshold_createsPendingApproval() {
        setSecurityWithApprove();
        SupplierInvoiceDto inv = apInvoice(INVOICE_ID, new BigDecimal("300000"), BigDecimal.ZERO,
            SupplierInvoiceStatus.POSTED);
        when(supplierInvoiceService.get(inv.uid())).thenReturn(inv);

        DebtWriteOff saved = buildApWriteOff(ABOVE_THRESHOLD, DebtWriteOffStatus.PENDING_APPROVAL);
        when(writeOffs.save(any())).thenReturn(saved);

        CreateDebtWriteOffRequestDto req = new CreateDebtWriteOffRequestDto(
            DebtWriteOffTargetKind.SUPPLIER_INVOICE, inv.uid(), ABOVE_THRESHOLD, "AP bad debt");

        DebtWriteOffDto dto = service.create(req);

        assertThat(dto.status()).isEqualTo(DebtWriteOffStatus.PENDING_APPROVAL);
        verify(supplierInvoiceService, never()).applyWriteOff(any(), any());
    }

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------

    private SalesInvoiceDto arInvoice(Long id, BigDecimal total, BigDecimal paid,
                                       SalesInvoiceStatus status) {
        return new SalesInvoiceDto(
            id, UidGenerator.next(), "SI-0001",
            COMPANY_ID, BRANCH_ID, CUSTOMER_ID, null,
            LocalDate.now(), LocalDate.now().plusDays(30),
            PaymentTerms.CREDIT, "TZS", 1L,
            total, BigDecimal.ZERO, BigDecimal.ZERO, total, paid,
            status, null, null, null, null, null, null, null,
            false, null, null, 0, null, null, List.of()
        );
    }

    private SupplierInvoiceDto apInvoice(Long id, BigDecimal total, BigDecimal paid,
                                          SupplierInvoiceStatus status) {
        return new SupplierInvoiceDto(
            id, UidGenerator.next(), "SINV-0001", "SUP-INV-001",
            COMPANY_ID, BRANCH_ID, SUPPLIER_ID,
            LocalDate.now(), LocalDate.now().plusDays(30),
            "TZS", total, BigDecimal.ZERO, total, paid,
            status, null, null, null, List.of()
        );
    }

    private DebtWriteOff buildWriteOff(BigDecimal amount, DebtWriteOffStatus status) {
        DebtWriteOff wo = DebtWriteOff.builder()
            .companyId(COMPANY_ID)
            .branchId(BRANCH_ID)
            .targetKind(DebtWriteOffTargetKind.CUSTOMER_INVOICE)
            .targetInvoiceId(INVOICE_ID)
            .targetInvoiceUid(UidGenerator.next())
            .amount(amount)
            .currencyCode("TZS")
            .reason("reason")
            .status(status)
            .requestedByUserId(REQUESTER_ID)
            .requestedAt(Instant.now())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        ReflectionTestUtils.setField(wo, "uid", UidGenerator.next());
        ReflectionTestUtils.setField(wo, "id", 1000L);
        return wo;
    }

    private DebtWriteOff buildApWriteOff(BigDecimal amount, DebtWriteOffStatus status) {
        String invoiceUid = UidGenerator.next();
        DebtWriteOff wo = DebtWriteOff.builder()
            .companyId(COMPANY_ID)
            .branchId(BRANCH_ID)
            .targetKind(DebtWriteOffTargetKind.SUPPLIER_INVOICE)
            .targetInvoiceId(INVOICE_ID)
            .targetInvoiceUid(invoiceUid)
            .amount(amount)
            .currencyCode("TZS")
            .reason("reason")
            .status(status)
            .requestedByUserId(REQUESTER_ID)
            .requestedAt(Instant.now())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        ReflectionTestUtils.setField(wo, "uid", UidGenerator.next());
        ReflectionTestUtils.setField(wo, "id", 1001L);
        lenient().when(supplierInvoiceService.get(invoiceUid))
            .thenThrow(new NoSuchElementException("not found"));
        return wo;
    }

    private void setSecurityWithApprove() {
        var auth = new TestingAuthenticationToken("alice", null,
            List.of(new SimpleGrantedAuthority("DEBT.WRITE_OFF.APPROVE"),
                    new SimpleGrantedAuthority("DEBT.WRITE_OFF.REQUEST")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setSecurityWithoutApprove() {
        var auth = new TestingAuthenticationToken("bob", null,
            List.of(new SimpleGrantedAuthority("DEBT.WRITE_OFF.REQUEST")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}

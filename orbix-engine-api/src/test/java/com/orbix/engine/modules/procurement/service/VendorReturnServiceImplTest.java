package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.procurement.domain.dto.ApplyVendorCreditNoteRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.CreateVendorReturnRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.IssueVendorCreditNoteRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.VendorCreditNoteDto;
import com.orbix.engine.modules.procurement.domain.dto.VendorReturnDto;
import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoice;
import com.orbix.engine.modules.procurement.domain.entity.VendorCreditNote;
import com.orbix.engine.modules.procurement.domain.entity.VendorCreditNoteAllocation;
import com.orbix.engine.modules.procurement.domain.entity.VendorReturn;
import com.orbix.engine.modules.procurement.domain.entity.VendorReturnLine;
import com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus;
import com.orbix.engine.modules.procurement.domain.enums.VendorCreditNoteStatus;
import com.orbix.engine.modules.procurement.domain.enums.VendorReturnReason;
import com.orbix.engine.modules.procurement.domain.enums.VendorReturnStatus;
import com.orbix.engine.modules.procurement.repository.SupplierInvoiceRepository;
import com.orbix.engine.modules.procurement.repository.VendorCreditNoteAllocationRepository;
import com.orbix.engine.modules.procurement.repository.VendorCreditNoteRepository;
import com.orbix.engine.modules.procurement.repository.VendorReturnLineRepository;
import com.orbix.engine.modules.procurement.repository.VendorReturnRepository;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.service.StockMoveService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorReturnServiceImplTest {

    private static final Long COMPANY_ID   = 7L;
    private static final Long BRANCH_ID    = 12L;
    private static final Long SUPPLIER_ID  = 808L;
    private static final Long ITEM_ID      = 8801L;
    private static final Long UOM_ID       = 1L;
    private static final Long VAT_GROUP_ID = 2L;
    private static final Long ACTOR_ID     = 4L;
    private static final Long INVOICE_ID   = 500L;

    @Mock private VendorReturnRepository returns;
    @Mock private VendorReturnLineRepository lines;
    @Mock private VendorCreditNoteRepository creditNotes;
    @Mock private VendorCreditNoteAllocationRepository allocations;
    @Mock private SupplierInvoiceRepository invoices;
    @Mock private SupplierInvoiceService supplierInvoiceService;
    @Mock private ItemRepository items;
    @Mock private VatGroupRepository vatGroups;
    @Mock private AppUserRepository users;
    @Mock private StockMoveService stockMoveService;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;

    @InjectMocks private VendorReturnServiceImpl service;

    private final AtomicLong nextId = new AtomicLong(1000);

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);

        Item item = new Item(COMPANY_ID, "SKU1", "Sugar", ItemType.BOTH, 10L, UOM_ID, VAT_GROUP_ID, ACTOR_ID);
        item.setId(ITEM_ID);
        lenient().when(items.findById(ITEM_ID)).thenReturn(Optional.of(item));

        VatGroup vat = new VatGroup(COMPANY_ID, "STD", "Standard", new BigDecimal("0.18"),
            LocalDate.of(2020, 1, 1), true, ACTOR_ID);
        vat.setId(VAT_GROUP_ID);
        lenient().when(vatGroups.findById(VAT_GROUP_ID)).thenReturn(Optional.of(vat));

        lenient().when(returns.save(any(VendorReturn.class))).thenAnswer(inv -> {
            VendorReturn r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(nextId.getAndIncrement());
                ReflectionTestUtils.setField(r, "uid", UidGenerator.next());
            }
            return r;
        });
        lenient().when(lines.save(any(VendorReturnLine.class))).thenAnswer(inv -> {
            VendorReturnLine l = inv.getArgument(0);
            l.setId(nextId.getAndIncrement());
            return l;
        });
        lenient().when(creditNotes.save(any(VendorCreditNote.class))).thenAnswer(inv -> {
            VendorCreditNote cn = inv.getArgument(0);
            if (cn.getId() == null) {
                cn.setId(nextId.getAndIncrement());
                ReflectionTestUtils.setField(cn, "uid", UidGenerator.next());
            }
            return cn;
        });
        lenient().when(allocations.save(any(VendorCreditNoteAllocation.class))).thenAnswer(inv -> {
            VendorCreditNoteAllocation a = inv.getArgument(0);
            if (a.getId() == null) a.setId(nextId.getAndIncrement());
            return a;
        });
        lenient().when(lines.findByVendorReturnIdOrderByLineNoAsc(any())).thenReturn(List.of());
        lenient().when(allocations.findByVendorCreditNoteIdOrderByAllocatedAtAsc(any())).thenReturn(List.of());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private VendorReturn draftReturn() {
        VendorReturn r = new VendorReturn("RET-001", COMPANY_ID, BRANCH_ID, SUPPLIER_ID,
            null, null, LocalDate.of(2026, 5, 28), VendorReturnReason.DAMAGED, true, null, ACTOR_ID);
        r.setId(200L);
        ReflectionTestUtils.setField(r, "uid", UidGenerator.next());
        return r;
    }

    private VendorReturnLine returnLine(Long returnId) {
        VendorReturnLine l = new VendorReturnLine(returnId, 1, ITEM_ID, UOM_ID,
            new BigDecimal("5"), new BigDecimal("100"), VAT_GROUP_ID,
            new BigDecimal("90.0000"), new BigDecimal("590.0000"), null);
        l.setId(300L);
        return l;
    }

    private VendorCreditNote postedCreditNote(Long returnId, BigDecimal total) {
        VendorCreditNote cn = new VendorCreditNote("VCN-001", COMPANY_ID, BRANCH_ID, SUPPLIER_ID,
            returnId, LocalDate.of(2026, 5, 28), "TZS", total, null, ACTOR_ID);
        cn.setId(400L);
        ReflectionTestUtils.setField(cn, "uid", UidGenerator.next());
        return cn;
    }

    private SupplierInvoice postedInvoice(BigDecimal total, BigDecimal paid) {
        SupplierInvoice inv = new SupplierInvoice("INV-001", "SINV-001", COMPANY_ID, BRANCH_ID,
            SUPPLIER_ID, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
            "TZS", total, BigDecimal.ZERO, null, ACTOR_ID);
        inv.setId(INVOICE_ID);
        ReflectionTestUtils.setField(inv, "uid", UidGenerator.next());
        inv.post(ACTOR_ID); // DRAFT → POSTED
        if (paid.signum() > 0) {
            inv.applyPayment(paid, ACTOR_ID);
        }
        return inv;
    }

    private CreateVendorReturnRequestDto createRequest() {
        return new CreateVendorReturnRequestDto(
            "RET-001", BRANCH_ID, SUPPLIER_ID, null, null,
            LocalDate.of(2026, 5, 28), VendorReturnReason.DAMAGED, true, null,
            List.of(new CreateVendorReturnRequestDto.LineDto(
                ITEM_ID, UOM_ID, new BigDecimal("5"), new BigDecimal("100"), VAT_GROUP_ID, null
            ))
        );
    }

    // ── create draft tests ────────────────────────────────────────────────────

    @Test
    void createDraft_persistsAndReturnsDto() {
        when(returns.existsByBranchIdAndNumber(BRANCH_ID, "RET-001")).thenReturn(false);

        VendorReturnDto dto = service.createDraft(createRequest());

        assertThat(dto.status()).isEqualTo(VendorReturnStatus.DRAFT);
        assertThat(dto.supplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(dto.reason()).isEqualTo(VendorReturnReason.DAMAGED);
        assertThat(dto.restock()).isTrue();
        verify(returns).save(any(VendorReturn.class));
        verify(events).publish(eq("VendorReturnCreated.v1"), any(), any(), any());
    }

    @Test
    void createDraft_duplicateNumber_throws() {
        when(returns.existsByBranchIdAndNumber(BRANCH_ID, "RET-001")).thenReturn(true);

        assertThatThrownBy(() -> service.createDraft(createRequest()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(returns, never()).save(any());
    }

    @Test
    void createDraft_crossSupplierOriginalInvoice_throws() {
        SupplierInvoice inv = new SupplierInvoice("INV-X", "SINV-X", COMPANY_ID, BRANCH_ID,
            999L /* different supplier */, LocalDate.now(), LocalDate.now().plusDays(30),
            "TZS", new BigDecimal("1000"), BigDecimal.ZERO, null, ACTOR_ID);
        inv.setId(888L);
        when(returns.existsByBranchIdAndNumber(any(), any())).thenReturn(false);
        when(invoices.findById(888L)).thenReturn(Optional.of(inv));

        CreateVendorReturnRequestDto req = new CreateVendorReturnRequestDto(
            "RET-002", BRANCH_ID, SUPPLIER_ID, null, 888L,
            LocalDate.of(2026, 5, 28), VendorReturnReason.WRONG_ITEM, true, null,
            List.of(new CreateVendorReturnRequestDto.LineDto(
                ITEM_ID, UOM_ID, new BigDecimal("1"), new BigDecimal("100"), VAT_GROUP_ID, null
            ))
        );

        assertThatThrownBy(() -> service.createDraft(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("different supplier");
    }

    // ── post tests ────────────────────────────────────────────────────────────

    @Test
    void post_restock_true_postsReturnOutMove() {
        VendorReturn ret = draftReturn();
        VendorReturnLine line = returnLine(ret.getId());
        when(returns.findByUid(ret.getUid())).thenReturn(Optional.of(ret));
        when(lines.findByVendorReturnIdOrderByLineNoAsc(ret.getId())).thenReturn(List.of(line));

        VendorReturnDto dto = service.post(ret.getUid());

        assertThat(dto.status()).isEqualTo(VendorReturnStatus.POSTED);
        ArgumentCaptor<PostStockMoveRequestDto> cap = ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(cap.capture());
        PostStockMoveRequestDto move = cap.getValue();
        assertThat(move.moveType()).isEqualTo(StockMoveType.RETURN_OUT);
        assertThat(move.qty()).isEqualByComparingTo("-5"); // negated
        verify(events).publish(eq("VendorReturnPosted.v1"), any(), any(), any());
    }

    @Test
    void post_restock_false_postsDamageMove() {
        VendorReturn ret = new VendorReturn("RET-002", COMPANY_ID, BRANCH_ID, SUPPLIER_ID,
            null, null, LocalDate.of(2026, 5, 28), VendorReturnReason.DAMAGED, false, null, ACTOR_ID);
        ret.setId(201L);
        ReflectionTestUtils.setField(ret, "uid", UidGenerator.next());
        VendorReturnLine line = returnLine(201L);

        when(returns.findByUid(ret.getUid())).thenReturn(Optional.of(ret));
        when(lines.findByVendorReturnIdOrderByLineNoAsc(201L)).thenReturn(List.of(line));

        service.post(ret.getUid());

        ArgumentCaptor<PostStockMoveRequestDto> cap = ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(cap.capture());
        assertThat(cap.getValue().moveType()).isEqualTo(StockMoveType.DAMAGE);
    }

    @Test
    void post_alreadyPosted_throws() {
        VendorReturn ret = draftReturn();
        ret.post(ACTOR_ID);
        when(returns.findByUid(ret.getUid())).thenReturn(Optional.of(ret));
        when(lines.findByVendorReturnIdOrderByLineNoAsc(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.post(ret.getUid()))
            .isInstanceOf(IllegalStateException.class);
    }

    // ── cancel tests ──────────────────────────────────────────────────────────

    @Test
    void cancel_fromDraft_succeeds() {
        VendorReturn ret = draftReturn();
        when(returns.findByUid(ret.getUid())).thenReturn(Optional.of(ret));

        VendorReturnDto dto = service.cancel(ret.getUid());

        assertThat(dto.status()).isEqualTo(VendorReturnStatus.CANCELLED);
        verify(events).publish(eq("VendorReturnCancelled.v1"), any(), any(), any());
    }

    @Test
    void cancel_fromPosted_throws() {
        VendorReturn ret = draftReturn();
        ret.post(ACTOR_ID);
        when(returns.findByUid(ret.getUid())).thenReturn(Optional.of(ret));

        assertThatThrownBy(() -> service.cancel(ret.getUid()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("POSTED");
    }

    // ── issue credit note tests ───────────────────────────────────────────────

    @Test
    void issueCreditNote_fromPostedReturn_createsCreditNote() {
        VendorReturn ret = draftReturn();
        ret.post(ACTOR_ID);
        when(returns.findByUid(ret.getUid())).thenReturn(Optional.of(ret));
        when(creditNotes.existsByBranchIdAndNumber(BRANCH_ID, "VCN-001")).thenReturn(false);

        VendorCreditNoteDto dto = service.issueCreditNote(
            ret.getUid(), new IssueVendorCreditNoteRequestDto("VCN-001", null));

        assertThat(dto.status()).isEqualTo(VendorCreditNoteStatus.POSTED);
        assertThat(dto.supplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(ret.getStatus()).isEqualTo(VendorReturnStatus.CREDITED);
        verify(creditNotes).save(any(VendorCreditNote.class));
        verify(events).publish(eq("VendorCreditNoteIssued.v1"), any(), any(), any());
    }

    @Test
    void issueCreditNote_fromDraft_throws() {
        VendorReturn ret = draftReturn(); // still DRAFT
        when(returns.findByUid(ret.getUid())).thenReturn(Optional.of(ret));
        when(creditNotes.existsByBranchIdAndNumber(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.issueCreditNote(
            ret.getUid(), new IssueVendorCreditNoteRequestDto("VCN-001", null)))
            .isInstanceOf(IllegalStateException.class);
    }

    // ── applyToInvoice happy path ─────────────────────────────────────────────

    @Test
    void applyToInvoice_happyPath_allocatesAndUpdatesStatus() {
        BigDecimal total = new BigDecimal("1000.0000");
        VendorCreditNote cn = postedCreditNote(200L, total);
        SupplierInvoice inv = postedInvoice(new BigDecimal("2000.0000"), BigDecimal.ZERO);

        when(creditNotes.findByUid(cn.getUid())).thenReturn(Optional.of(cn));
        when(invoices.findByUid(inv.getUid())).thenReturn(Optional.of(inv));

        ApplyVendorCreditNoteRequestDto req = new ApplyVendorCreditNoteRequestDto(
            inv.getUid(), new BigDecimal("600.0000"));

        VendorCreditNoteDto result = service.applyToInvoice(cn.getUid(), req);

        assertThat(result.allocatedAmount()).isEqualByComparingTo("600");
        assertThat(result.status()).isEqualTo(VendorCreditNoteStatus.PARTIALLY_ALLOCATED);
        verify(allocations).save(any(VendorCreditNoteAllocation.class));
        verify(supplierInvoiceService).applyVendorCredit(eq(INVOICE_ID), eq(new BigDecimal("600.0000")));
        verify(events).publish(eq("VendorCreditNoteApplied.v1"), any(), any(), any());
    }

    @Test
    void applyToInvoice_fullAmount_movesToFullyAllocated() {
        BigDecimal total = new BigDecimal("1000.0000");
        VendorCreditNote cn = postedCreditNote(200L, total);
        SupplierInvoice inv = postedInvoice(new BigDecimal("2000.0000"), BigDecimal.ZERO);

        when(creditNotes.findByUid(cn.getUid())).thenReturn(Optional.of(cn));
        when(invoices.findByUid(inv.getUid())).thenReturn(Optional.of(inv));

        VendorCreditNoteDto result = service.applyToInvoice(
            cn.getUid(), new ApplyVendorCreditNoteRequestDto(inv.getUid(), total));

        assertThat(result.status()).isEqualTo(VendorCreditNoteStatus.FULLY_ALLOCATED);
    }

    @Test
    void applyToInvoice_overApply_throws() {
        BigDecimal total = new BigDecimal("500.0000");
        VendorCreditNote cn = postedCreditNote(200L, total);
        // amount > available — service throws before loading the invoice
        when(creditNotes.findByUid(cn.getUid())).thenReturn(Optional.of(cn));

        assertThatThrownBy(() -> service.applyToInvoice(
            cn.getUid(), new ApplyVendorCreditNoteRequestDto(UidGenerator.next(), new BigDecimal("600"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds available credit");
        verify(supplierInvoiceService, never()).applyVendorCredit(any(), any());
    }

    @Test
    void applyToInvoice_crossSupplier_throws() {
        BigDecimal total = new BigDecimal("500.0000");
        VendorCreditNote cn = postedCreditNote(200L, total); // supplierId = 808
        SupplierInvoice inv = new SupplierInvoice("INV-X", "SINV-X", COMPANY_ID, BRANCH_ID,
            999L /* different supplier */, LocalDate.now(), LocalDate.now().plusDays(30),
            "TZS", new BigDecimal("2000"), BigDecimal.ZERO, null, ACTOR_ID);
        inv.setId(INVOICE_ID);
        ReflectionTestUtils.setField(inv, "uid", UidGenerator.next());

        when(creditNotes.findByUid(cn.getUid())).thenReturn(Optional.of(cn));
        when(invoices.findByUid(inv.getUid())).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.applyToInvoice(
            cn.getUid(), new ApplyVendorCreditNoteRequestDto(inv.getUid(), new BigDecimal("100"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("different supplier");
    }

    @Test
    void applyToInvoice_alreadyFullyAllocated_throws() {
        BigDecimal total = new BigDecimal("500.0000");
        VendorCreditNote cn = postedCreditNote(200L, total);
        // Manually set to FULLY_ALLOCATED — service throws before loading the invoice
        cn.setStatus(VendorCreditNoteStatus.FULLY_ALLOCATED);
        cn.setAllocatedAmount(total);

        when(creditNotes.findByUid(cn.getUid())).thenReturn(Optional.of(cn));

        assertThatThrownBy(() -> service.applyToInvoice(
            cn.getUid(), new ApplyVendorCreditNoteRequestDto(UidGenerator.next(), new BigDecimal("100"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("FULLY_ALLOCATED");
    }

    @Test
    void applyToInvoice_invoiceOverPay_throws() {
        BigDecimal total = new BigDecimal("1000.0000");
        VendorCreditNote cn = postedCreditNote(200L, total);
        SupplierInvoice inv = postedInvoice(new BigDecimal("200.0000"), BigDecimal.ZERO);

        when(creditNotes.findByUid(cn.getUid())).thenReturn(Optional.of(cn));
        when(invoices.findByUid(inv.getUid())).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.applyToInvoice(
            cn.getUid(), new ApplyVendorCreditNoteRequestDto(inv.getUid(), new BigDecimal("500"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds invoice outstanding");
    }

    // ── tenant guard ──────────────────────────────────────────────────────────

    @Test
    void get_differentTenant_throws() {
        VendorReturn ret = new VendorReturn("RET-X", 99L /* wrong company */, BRANCH_ID, SUPPLIER_ID,
            null, null, LocalDate.now(), VendorReturnReason.OTHER, true, null, ACTOR_ID);
        ret.setId(999L);
        ReflectionTestUtils.setField(ret, "uid", UidGenerator.next());
        when(returns.findByUid(ret.getUid())).thenReturn(Optional.of(ret));

        assertThatThrownBy(() -> service.get(ret.getUid()))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void applyToInvoice_differentTenantCreditNote_throws() {
        VendorCreditNote cn = postedCreditNote(200L, new BigDecimal("500"));
        // Override company to a different one
        cn.setCompanyId(99L);
        when(creditNotes.findByUid(cn.getUid())).thenReturn(Optional.of(cn));

        assertThatThrownBy(() -> service.applyToInvoice(
            cn.getUid(), new ApplyVendorCreditNoteRequestDto(UidGenerator.next(), new BigDecimal("100"))))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ── list tests ────────────────────────────────────────────────────────────

    @Test
    void list_noScope_returnsPagedResults() {
        VendorReturn ret = draftReturn();
        Page<VendorReturn> page = new PageImpl<>(List.of(ret));
        when(branchScope.requireReadable(null)).thenReturn(null);
        when(returns.findByCompanyIdOrderByIdDesc(eq(COMPANY_ID), any())).thenReturn(page);

        var result = service.list(null, PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
    }
}

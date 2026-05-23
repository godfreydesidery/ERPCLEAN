package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.admin.domain.entity.Section;
import com.orbix.engine.modules.admin.domain.enums.SectionType;
import com.orbix.engine.modules.admin.repository.SectionRepository;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.party.domain.entity.Customer;
import com.orbix.engine.modules.party.repository.CustomerRepository;
import com.orbix.engine.modules.pos.domain.dto.PosSaleDto;
import com.orbix.engine.modules.pos.domain.dto.PostPosSaleRequestDto;
import com.orbix.engine.modules.pos.domain.entity.PosPayment;
import com.orbix.engine.modules.pos.domain.entity.PosSale;
import com.orbix.engine.modules.pos.domain.entity.PosSaleLine;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.PosPaymentMethod;
import com.orbix.engine.modules.pos.domain.enums.PosSaleStatus;
import com.orbix.engine.modules.pos.repository.PosPaymentRepository;
import com.orbix.engine.modules.pos.repository.PosSaleLineRepository;
import com.orbix.engine.modules.pos.repository.PosSaleRepository;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
import com.orbix.engine.modules.stock.domain.dto.BatchPickDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.service.StockBatchService;
import com.orbix.engine.modules.stock.service.StockMoveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
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
class PosSaleServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long TILL_ID = 100L;
    private static final Long SESSION_ID = 200L;
    private static final Long SECTION_ID = 33L;
    private static final Long CUSTOMER_ID = 540L;
    private static final Long ITEM_ID = 8801L;
    private static final Long UOM_ID = 1L;
    private static final Long VAT_GROUP_ID = 2L;
    private static final Long ACTOR_ID = 4L;

    @Mock private PosSaleRepository sales;
    @Mock private PosSaleLineRepository lines;
    @Mock private PosPaymentRepository payments;
    @Mock private TillSessionRepository tillSessions;
    @Mock private com.orbix.engine.modules.pos.repository.TillCurrencyRepository tillCurrencies;
    @Mock private SectionRepository sections;
    @Mock private ItemRepository items;
    @Mock private VatGroupRepository vatGroups;
    @Mock private CustomerRepository customers;
    @Mock private com.orbix.engine.modules.admin.repository.CompanyRepository companies;
    @Mock private com.orbix.engine.modules.admin.repository.FxRateRepository fxRates;
    @Mock private ItemBranchBalanceRepository balances;
    @Mock private StockMoveService stockMoveService;
    @Mock private StockBatchService stockBatchService;
    @Mock private com.orbix.engine.modules.cash.service.CashLedgerService cashLedger;
    @Mock private com.orbix.engine.modules.giftcard.service.GiftCardService giftCards;
    @Mock private com.orbix.engine.modules.day.service.DayGuard dayGuard;
    @Mock private com.orbix.engine.modules.iam.service.PermissionResolverService permissions;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;
    @Mock private com.orbix.engine.modules.iam.service.BranchScope branchScope;

    @InjectMocks private PosSaleServiceImpl service;

    private final AtomicLong nextId = new AtomicLong(9000);

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        org.springframework.test.util.ReflectionTestUtils.setField(service,
            "discountThresholdPct", new BigDecimal("10"));
        org.springframework.test.util.ReflectionTestUtils.setField(service,
            "refundThreshold", new BigDecimal("10000"));

        TillSession session = openSession();
        lenient().when(tillSessions.findById(SESSION_ID)).thenReturn(Optional.of(session));

        Section section = new Section(BRANCH_ID, "MAIN", "Main floor", SectionType.RETAIL_FLOOR, ACTOR_ID);
        section.setId(SECTION_ID);
        lenient().when(sections.findById(SECTION_ID)).thenReturn(Optional.of(section));

        Customer customer = new Customer(CUSTOMER_ID);
        lenient().when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        Item item = new Item(COMPANY_ID, "SKU1", "Sugar", ItemType.SELLABLE, 10L, UOM_ID, VAT_GROUP_ID, ACTOR_ID);
        item.setId(ITEM_ID);
        lenient().when(items.findById(ITEM_ID)).thenReturn(Optional.of(item));

        VatGroup vat = new VatGroup(COMPANY_ID, "STD", "Standard", new BigDecimal("0.18"),
            LocalDate.of(2020, 1, 1), true, ACTOR_ID);
        vat.setId(VAT_GROUP_ID);
        lenient().when(vatGroups.findById(VAT_GROUP_ID)).thenReturn(Optional.of(vat));

        // F5.6: company functional currency = TZS; no FX currencies registered on the till by default.
        com.orbix.engine.modules.admin.domain.entity.Company company =
            new com.orbix.engine.modules.admin.domain.entity.Company(
                1L, "ACME", "Acme", "TZS", "TZ", "Africa/Dar_es_Salaam", ACTOR_ID);
        company.setId(COMPANY_ID);
        lenient().when(companies.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        lenient().when(tillCurrencies.existsByIdTillIdAndIdCurrencyCode(any(), any()))
            .thenReturn(false);

        lenient().when(sales.findByCompanyIdAndClientOpId(any(), any())).thenReturn(Optional.empty());
        lenient().when(sales.existsByCompanyIdAndNumber(any(), any())).thenReturn(false);
        lenient().when(sales.save(any(PosSale.class))).thenAnswer(inv -> {
            PosSale s = inv.getArgument(0);
            if (s.getId() == null) s.setId(nextId.getAndIncrement());
            return s;
        });
        lenient().when(lines.save(any(PosSaleLine.class))).thenAnswer(inv -> {
            PosSaleLine l = inv.getArgument(0);
            l.setId(nextId.getAndIncrement());
            return l;
        });
        lenient().when(payments.save(any(PosPayment.class))).thenAnswer(inv -> {
            PosPayment p = inv.getArgument(0);
            p.setId(nextId.getAndIncrement());
            return p;
        });
    }

    private TillSession openSession() {
        TillSession s = new TillSession(TILL_ID, BRANCH_ID, COMPANY_ID,
            LocalDate.of(2026, 5, 13), ACTOR_ID, new BigDecimal("50000"));
        s.setId(SESSION_ID);
        return s;
    }

    private PostPosSaleRequestDto request(String number, String clientOpId, BigDecimal qty,
                                          BigDecimal price, BigDecimal tender) {
        return new PostPosSaleRequestDto(
            number, clientOpId, SESSION_ID, SECTION_ID, CUSTOMER_ID, null, null,
            Instant.parse("2026-05-13T10:00:00Z"), null,
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID, qty, price, null, VAT_GROUP_ID)),
            List.of(new PostPosSaleRequestDto.Payment(PosPaymentMethod.CASH, tender, null, null, null, null)),
            null
        );
    }

    @Test
    void post_singleLineCash_writesSaleAndStockMove() {
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
            .thenReturn(Optional.of(balance(new BigDecimal("75"))));

        // qty 2 * price 100 = 200 subtotal; tax 36; total 236; tender 250; change 14
        PosSaleDto dto = service.post(request("TILL-1-0001", "op-1",
            new BigDecimal("2"), new BigDecimal("100"), new BigDecimal("250")));

        assertThat(dto.status()).isEqualTo(PosSaleStatus.POSTED);
        assertThat(dto.subtotalAmount()).isEqualByComparingTo("200");
        assertThat(dto.taxAmount()).isEqualByComparingTo("36");
        assertThat(dto.totalAmount()).isEqualByComparingTo("236");
        assertThat(dto.tenderedAmount()).isEqualByComparingTo("250");
        assertThat(dto.changeAmount()).isEqualByComparingTo("14");

        ArgumentCaptor<PostStockMoveRequestDto> captor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(captor.capture());
        PostStockMoveRequestDto move = captor.getValue();
        assertThat(move.moveType()).isEqualTo(StockMoveType.SALE);
        assertThat(move.qty()).isEqualByComparingTo("-2");
        assertThat(move.batchId()).isNull();
        verify(events).publish(eq("PosSaleClosed.v1"), any(), any(), any());
    }

    @Test
    void post_tenderBelowTotal_isRejected() {
        // total 236; tender 200 — reject before persistence
        PostPosSaleRequestDto req = request("TILL-1-S", "op-short",
            new BigDecimal("2"), new BigDecimal("100"), new BigDecimal("200"));
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("less than total");
        verify(sales, never()).save(any());
    }

    @Test
    void post_mixedTender_sumsCorrectlyAndZeroChange() {
        when(balances.findById(any(ItemBranchBalanceId.class)))
            .thenReturn(Optional.of(balance(new BigDecimal("80"))));

        PostPosSaleRequestDto req = new PostPosSaleRequestDto(
            "TILL-1-M", "op-mix", SESSION_ID, SECTION_ID, CUSTOMER_ID, null, null,
            Instant.parse("2026-05-13T11:00:00Z"), null,
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID,
                new BigDecimal("1"), new BigDecimal("100"), null, VAT_GROUP_ID)),
            // total 118; cash 50 + card 68 = 118
            List.of(
                new PostPosSaleRequestDto.Payment(PosPaymentMethod.CASH, new BigDecimal("50"), null, null, null, null),
                new PostPosSaleRequestDto.Payment(PosPaymentMethod.CARD, new BigDecimal("68"), null, "AUTH-1", "T1", "1234")
            ),
            null
        );

        PosSaleDto dto = service.post(req);

        assertThat(dto.totalAmount()).isEqualByComparingTo("118");
        assertThat(dto.tenderedAmount()).isEqualByComparingTo("118");
        assertThat(dto.changeAmount()).isEqualByComparingTo("0");
        assertThat(dto.payments()).hasSize(2);
    }

    @Test
    void post_idempotent_returnsExistingOnSameClientOpId() {
        PosSale existing = persistedSale("TILL-1-X", "op-dup");
        when(sales.findByCompanyIdAndClientOpId(COMPANY_ID, "op-dup"))
            .thenReturn(Optional.of(existing));
        when(lines.findByPosSaleIdOrderByLineNoAsc(existing.getId())).thenReturn(List.of());
        when(payments.findByPosSaleIdOrderByIdAsc(existing.getId())).thenReturn(List.of());

        PosSaleDto dto = service.post(request("TILL-1-X-NEW", "op-dup",
            new BigDecimal("999"), new BigDecimal("999"), new BigDecimal("9999")));

        assertThat(dto.id()).isEqualTo(existing.getId());
        verify(sales, never()).save(any());
        verify(stockMoveService, never()).post(any());
        verify(events, never()).publish(eq("PosSaleClosed.v1"), any(), any(), any());
    }

    @Test
    void post_sessionNotOpen_isRejected() {
        TillSession session = openSession();
        session.close(new BigDecimal("50000"), new BigDecimal("50000"), ACTOR_ID, null, null);
        when(tillSessions.findById(SESSION_ID)).thenReturn(Optional.of(session));

        PostPosSaleRequestDto req = request("TILL-1-C", "op-closed",
            new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("118"));
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CLOSED");
    }

    @Test
    void post_sectionFromWrongBranch_isRejected() {
        Section foreign = new Section(999L, "OTHER", "Wrong branch", SectionType.RETAIL_FLOOR, ACTOR_ID);
        foreign.setId(SECTION_ID);
        when(sections.findById(SECTION_ID)).thenReturn(Optional.of(foreign));

        PostPosSaleRequestDto req = request("TILL-1-S", "op-section",
            new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("118"));
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not belong to branch");
    }

    @Test
    void post_duplicateNumber_rejected() {
        when(sales.existsByCompanyIdAndNumber(COMPANY_ID, "TILL-1-DUP")).thenReturn(true);

        PostPosSaleRequestDto req = request("TILL-1-DUP", "op-fresh",
            new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("118"));
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void post_batchTrackedItem_drainsFefoAndEmitsOneMovePerPick() {
        Item batched = new Item(COMPANY_ID, "MILK", "Milk", ItemType.SELLABLE, 10L, UOM_ID, VAT_GROUP_ID, ACTOR_ID);
        batched.setId(ITEM_ID);
        batched.setBatchTracked(true);
        when(items.findById(ITEM_ID)).thenReturn(Optional.of(batched));

        when(stockBatchService.drainFefo(ITEM_ID, BRANCH_ID, new BigDecimal("5")))
            .thenReturn(List.of(
                new BatchPickDto(101L, "B-A", new BigDecimal("2"), new BigDecimal("60")),
                new BatchPickDto(102L, "B-B", new BigDecimal("3"), new BigDecimal("70"))
            ));

        PostPosSaleRequestDto req = request("TILL-1-B", "op-batch",
            new BigDecimal("5"), new BigDecimal("100"), new BigDecimal("600"));
        service.post(req);

        ArgumentCaptor<PostStockMoveRequestDto> captor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService, org.mockito.Mockito.times(2)).post(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(PostStockMoveRequestDto::batchId)
            .containsExactly(101L, 102L);
    }

    @Test
    void post_dayClosed_propagatesFromStockMoveService() {
        // First sale: stockMoveService.post throws (day-guard inside it)
        when(balances.findById(any(ItemBranchBalanceId.class)))
            .thenReturn(Optional.of(balance(new BigDecimal("75"))));
        org.mockito.Mockito.doThrow(new IllegalStateException("No open business day for branch"))
            .when(stockMoveService).post(any());

        PostPosSaleRequestDto req = request("TILL-1-D", "op-day",
            new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("118"));
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No open business day");
    }

    @Test
    void post_persistsPaymentsWithReferenceDetails() {
        when(balances.findById(any(ItemBranchBalanceId.class)))
            .thenReturn(Optional.of(balance(new BigDecimal("80"))));

        PostPosSaleRequestDto req = new PostPosSaleRequestDto(
            "TILL-1-P", "op-pay", SESSION_ID, SECTION_ID, CUSTOMER_ID, null, null,
            Instant.parse("2026-05-13T12:00:00Z"), null,
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID,
                new BigDecimal("1"), new BigDecimal("100"), null, VAT_GROUP_ID)),
            List.of(new PostPosSaleRequestDto.Payment(PosPaymentMethod.CARD,
                new BigDecimal("118"), null, "AUTH-9", "POS-T", "9999")),
            null
        );

        PosSaleDto dto = service.post(req);
        assertThat(dto.payments()).hasSize(1);
        var pay = dto.payments().get(0);
        assertThat(pay.method()).isEqualTo(PosPaymentMethod.CARD);
        assertThat(pay.reference()).isEqualTo("AUTH-9");
        assertThat(pay.terminalId()).isEqualTo("POS-T");
        assertThat(pay.last4()).isEqualTo("9999");
    }

    private PosSale persistedSale(String number, String clientOpId) {
        PosSale s = new PosSale(number, clientOpId, SESSION_ID, TILL_ID, BRANCH_ID, COMPANY_ID,
            SECTION_ID, CUSTOMER_ID, ACTOR_ID, null,
            com.orbix.engine.modules.pos.domain.enums.PosSaleKind.SALE,
            Instant.parse("2026-05-13T10:00:00Z"),
            LocalDate.of(2026, 5, 13),
            new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("18"), new BigDecimal("118"),
            new BigDecimal("118"), BigDecimal.ZERO, null
        );
        s.setId(nextId.getAndIncrement());
        return s;
    }

    private ItemBranchBalance balance(BigDecimal avgCost) {
        ItemBranchBalance b = new ItemBranchBalance(ITEM_ID, BRANCH_ID);
        b.setQtyOnHand(new BigDecimal("100"));
        b.setAvgCost(avgCost);
        return b;
    }

    // ---- F5.3: discount-approval + void ------------------------------------

    @Test
    void post_lineDiscountAboveThreshold_requiresApprover() {
        // No balance stub — validation fires before stock-move post.
        PostPosSaleRequestDto req = new PostPosSaleRequestDto(
            "TILL-1-DSC", "op-disc", SESSION_ID, SECTION_ID, CUSTOMER_ID, null, null,
            Instant.parse("2026-05-13T13:00:00Z"), null,
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID,
                new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("15"), VAT_GROUP_ID)),
            List.of(new PostPosSaleRequestDto.Payment(PosPaymentMethod.CASH,
                new BigDecimal("100.30"), null, null, null, null)),
            null
        );
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("authoriser");
    }

    @Test
    void post_lineDiscountAboveThreshold_selfApprover_rejected() {
        PostPosSaleRequestDto req = new PostPosSaleRequestDto(
            "TILL-1-DSC2", "op-disc2", SESSION_ID, SECTION_ID, CUSTOMER_ID, null, ACTOR_ID,
            Instant.parse("2026-05-13T13:00:00Z"), null,
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID,
                new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("15"), VAT_GROUP_ID)),
            List.of(new PostPosSaleRequestDto.Payment(PosPaymentMethod.CASH,
                new BigDecimal("100.30"), null, null, null, null)),
            null
        );
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("your own");
    }

    @Test
    void post_lineDiscountAboveThreshold_approverMissingPermission_403() {
        when(permissions.resolve(99L, COMPANY_ID, null))
            .thenReturn(java.util.Set.of());

        PostPosSaleRequestDto req = new PostPosSaleRequestDto(
            "TILL-1-DSC3", "op-disc3", SESSION_ID, SECTION_ID, CUSTOMER_ID, null, 99L,
            Instant.parse("2026-05-13T13:00:00Z"), null,
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID,
                new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("15"), VAT_GROUP_ID)),
            List.of(new PostPosSaleRequestDto.Payment(PosPaymentMethod.CASH,
                new BigDecimal("100.30"), null, null, null, null)),
            null
        );
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
            .hasMessageContaining(PosSaleServiceImpl.DISCOUNT_APPROVE_PERMISSION);
    }

    @Test
    void post_lineDiscountAboveThreshold_approvedSupervisor_succeeds() {
        when(balances.findById(any(ItemBranchBalanceId.class)))
            .thenReturn(Optional.of(balance(new BigDecimal("75"))));
        when(permissions.resolve(99L, COMPANY_ID, null))
            .thenReturn(java.util.Set.of(PosSaleServiceImpl.DISCOUNT_APPROVE_PERMISSION));

        PostPosSaleRequestDto req = new PostPosSaleRequestDto(
            "TILL-1-DSC4", "op-disc4", SESSION_ID, SECTION_ID, CUSTOMER_ID, null, 99L,
            Instant.parse("2026-05-13T13:00:00Z"), null,
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID,
                new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("15"), VAT_GROUP_ID)),
            // net 85, tax 15.30, total 100.30
            List.of(new PostPosSaleRequestDto.Payment(PosPaymentMethod.CASH,
                new BigDecimal("100.30"), null, null, null, null)),
            null
        );
        PosSaleDto dto = service.post(req);
        assertThat(dto.totalAmount()).isEqualByComparingTo("100.30");
    }

    @Test
    void post_headerDiscount_appliedAfterTax() {
        when(balances.findById(any(ItemBranchBalanceId.class)))
            .thenReturn(Optional.of(balance(new BigDecimal("75"))));

        // Subtotal 100; line tax 18; header discount 10 applied after tax → total 108.
        // (Line tax in F5.3 is computed on pre-header-discount net to keep per-line
        // VAT independent of header-level adjustments.)
        PostPosSaleRequestDto req = new PostPosSaleRequestDto(
            "TILL-1-HD", "op-hd", SESSION_ID, SECTION_ID, CUSTOMER_ID, null, null,
            Instant.parse("2026-05-13T14:00:00Z"), new BigDecimal("10"),
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID,
                new BigDecimal("1"), new BigDecimal("100"), null, VAT_GROUP_ID)),
            List.of(new PostPosSaleRequestDto.Payment(PosPaymentMethod.CASH,
                new BigDecimal("108"), null, null, null, null)),
            null
        );
        PosSaleDto dto = service.post(req);
        assertThat(dto.discountAmount()).isEqualByComparingTo("10");
        assertThat(dto.totalAmount()).isEqualByComparingTo("108");
    }

    @Test
    void post_headerDiscount_exceedingSubtotal_isRejected() {
        PostPosSaleRequestDto req = new PostPosSaleRequestDto(
            "TILL-1-HDX", "op-hdx", SESSION_ID, SECTION_ID, CUSTOMER_ID, null, null,
            Instant.parse("2026-05-13T14:00:00Z"), new BigDecimal("999"),
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID,
                new BigDecimal("1"), new BigDecimal("100"), null, VAT_GROUP_ID)),
            List.of(new PostPosSaleRequestDto.Payment(PosPaymentMethod.CASH,
                new BigDecimal("100"), null, null, null, null)),
            null
        );
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds subtotal");
    }

    @Test
    void voidSale_sameDay_writesCompensatingMovesAndFlipsToVoided() {
        PosSale sale = persistedSale("TILL-1-V", "op-void");
        when(sales.findById(sale.getId())).thenReturn(Optional.of(sale));
        PosSaleLine line = new PosSaleLine(sale.getId(), 1, ITEM_ID, UOM_ID,
            new BigDecimal("2"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
            VAT_GROUP_ID, new BigDecimal("36"), new BigDecimal("236"));
        line.setId(nextId.getAndIncrement());
        line.setCostAmount(new BigDecimal("75"));
        when(lines.findByPosSaleIdOrderByLineNoAsc(sale.getId())).thenReturn(List.of(line));
        when(payments.findByPosSaleIdOrderByIdAsc(sale.getId())).thenReturn(List.of());

        BusinessDay day = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(day);

        PosSaleDto dto = service.voidSale(sale.getId(),
            new com.orbix.engine.modules.pos.domain.dto.VoidPosSaleRequestDto("operator error"));

        assertThat(dto.status()).isEqualTo(com.orbix.engine.modules.pos.domain.enums.PosSaleStatus.VOIDED);
        ArgumentCaptor<PostStockMoveRequestDto> captor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(captor.capture());
        PostStockMoveRequestDto move = captor.getValue();
        assertThat(move.moveType()).isEqualTo(StockMoveType.RETURN_IN);
        assertThat(move.qty()).isEqualByComparingTo("2");
        assertThat(move.unitCost()).isEqualByComparingTo("75");
        verify(events).publish(eq("PosSaleVoided.v1"), any(), any(), any());
    }

    @Test
    void voidSale_differentBusinessDay_isRejected() {
        PosSale sale = persistedSale("TILL-1-VD", "op-vd");
        when(sales.findById(sale.getId())).thenReturn(Optional.of(sale));
        BusinessDay day = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 14), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(day);

        Long id = sale.getId();
        var req = new com.orbix.engine.modules.pos.domain.dto.VoidPosSaleRequestDto("late");
        assertThatThrownBy(() -> service.voidSale(id, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("same business day");
        verify(stockMoveService, never()).post(any());
    }

    // ---- F5.5: refund -------------------------------------------------------

    private com.orbix.engine.modules.pos.domain.dto.PostPosRefundRequestDto refundRequest(
            String number, String opId, Long originalSaleId,
            BigDecimal qty, BigDecimal price, BigDecimal tender, Long supervisorId) {
        return new com.orbix.engine.modules.pos.domain.dto.PostPosRefundRequestDto(
            number, opId, SESSION_ID, originalSaleId, SECTION_ID, CUSTOMER_ID, supervisorId,
            Instant.parse("2026-05-13T15:00:00Z"),
            List.of(new com.orbix.engine.modules.pos.domain.dto.PostPosRefundRequestDto.Line(
                ITEM_ID, UOM_ID, qty, price, null, VAT_GROUP_ID)),
            List.of(new com.orbix.engine.modules.pos.domain.dto.PostPosRefundRequestDto.Payment(
                PosPaymentMethod.CASH, tender, null, null, null, null)),
            null
        );
    }

    private PosSaleLine originalLine(Long saleId, BigDecimal qty, BigDecimal cost) {
        PosSaleLine l = new PosSaleLine(saleId, 1, ITEM_ID, UOM_ID,
            qty, new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
            VAT_GROUP_ID, BigDecimal.ZERO, new BigDecimal("100"));
        l.setId(nextId.getAndIncrement());
        l.setCostAmount(cost);
        return l;
    }

    @Test
    void refund_sameDay_writesNewRefundSaleAndCompensatingMove() {
        PosSale original = persistedSale("TILL-1-R", "op-orig");
        when(sales.findById(original.getId())).thenReturn(Optional.of(original));
        when(lines.findByPosSaleIdOrderByLineNoAsc(original.getId()))
            .thenReturn(List.of(originalLine(original.getId(), new BigDecimal("2"), new BigDecimal("75"))));

        BusinessDay day = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(day);

        PosSaleDto dto = service.refund(refundRequest("TILL-1-R-REF", "op-ref",
            original.getId(), new BigDecimal("2"), new BigDecimal("100"),
            new BigDecimal("236"), null));

        assertThat(dto.kind()).isEqualTo(com.orbix.engine.modules.pos.domain.enums.PosSaleKind.REFUND);
        assertThat(dto.refundedFromSaleId()).isEqualTo(original.getId());
        assertThat(dto.status()).isEqualTo(PosSaleStatus.POSTED);
        assertThat(dto.totalAmount()).isEqualByComparingTo("236");
        assertThat(dto.changeAmount()).isEqualByComparingTo("0");

        ArgumentCaptor<PostStockMoveRequestDto> captor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(captor.capture());
        PostStockMoveRequestDto move = captor.getValue();
        assertThat(move.moveType()).isEqualTo(StockMoveType.RETURN_IN);
        assertThat(move.qty()).isEqualByComparingTo("2");
        assertThat(move.unitCost()).isEqualByComparingTo("75");
        verify(events).publish(eq("PosSaleRefunded.v1"), any(), any(), any());
    }

    @Test
    void refund_idempotent_returnsPriorOnSameClientOpId() {
        PosSale prior = persistedSale("TILL-1-R-PRIOR", "op-ref-dup");
        when(sales.findByCompanyIdAndClientOpId(COMPANY_ID, "op-ref-dup"))
            .thenReturn(Optional.of(prior));
        when(lines.findByPosSaleIdOrderByLineNoAsc(prior.getId())).thenReturn(List.of());
        when(payments.findByPosSaleIdOrderByIdAsc(prior.getId())).thenReturn(List.of());

        PosSaleDto dto = service.refund(refundRequest("TILL-1-R-NEW", "op-ref-dup",
            999L, new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("118"), null));

        assertThat(dto.id()).isEqualTo(prior.getId());
        verify(stockMoveService, never()).post(any());
        verify(events, never()).publish(eq("PosSaleRefunded.v1"), any(), any(), any());
    }

    @Test
    void refund_differentBusinessDay_isRejected() {
        PosSale original = persistedSale("TILL-1-R-D", "op-ref-d");
        when(sales.findById(original.getId())).thenReturn(Optional.of(original));
        BusinessDay nextDay = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 14), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(nextDay);

        Long origId = original.getId();
        var req = refundRequest("TILL-1-R-D-REF", "op-ref-late",
            origId, new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("118"), null);
        assertThatThrownBy(() -> service.refund(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("same business day");
        verify(stockMoveService, never()).post(any());
    }

    @Test
    void refund_batchTrackedItem_isRejected() {
        Item batched = new Item(COMPANY_ID, "MILK", "Milk", ItemType.SELLABLE, 10L, UOM_ID, VAT_GROUP_ID, ACTOR_ID);
        batched.setId(ITEM_ID);
        batched.setBatchTracked(true);
        when(items.findById(ITEM_ID)).thenReturn(Optional.of(batched));

        PosSale original = persistedSale("TILL-1-R-B", "op-ref-b");
        when(sales.findById(original.getId())).thenReturn(Optional.of(original));
        when(lines.findByPosSaleIdOrderByLineNoAsc(original.getId()))
            .thenReturn(List.of(originalLine(original.getId(), new BigDecimal("1"), new BigDecimal("60"))));
        BusinessDay day = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(day);

        Long origId = original.getId();
        var req = refundRequest("TILL-1-R-B-REF", "op-ref-bb",
            origId, new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("118"), null);
        assertThatThrownBy(() -> service.refund(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("batch-tracked");
        verify(stockMoveService, never()).post(any());
    }

    @Test
    void refund_tenderNotEqualTotal_isRejected() {
        PosSale original = persistedSale("TILL-1-R-T", "op-ref-t");
        when(sales.findById(original.getId())).thenReturn(Optional.of(original));
        when(lines.findByPosSaleIdOrderByLineNoAsc(original.getId()))
            .thenReturn(List.of(originalLine(original.getId(), new BigDecimal("1"), new BigDecimal("75"))));
        BusinessDay day = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(day);

        Long origId = original.getId();
        // total = 118; tender = 120 — mismatch
        var req = refundRequest("TILL-1-R-T-REF", "op-ref-tt",
            origId, new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("120"), null);
        assertThatThrownBy(() -> service.refund(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no change is paid");
        verify(stockMoveService, never()).post(any());
    }

    @Test
    void refund_aboveThreshold_requiresSupervisor() {
        PosSale original = persistedSale("TILL-1-R-X", "op-ref-x");
        when(sales.findById(original.getId())).thenReturn(Optional.of(original));
        when(lines.findByPosSaleIdOrderByLineNoAsc(original.getId()))
            .thenReturn(List.of(originalLine(original.getId(), new BigDecimal("120"), new BigDecimal("75"))));
        BusinessDay day = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(day);

        Long origId = original.getId();
        // 120 * 100 = 12000; tax 2160; total 14160 — above 10000 threshold, no supervisor
        var req = refundRequest("TILL-1-R-X-REF", "op-ref-xx",
            origId, new BigDecimal("120"), new BigDecimal("100"), new BigDecimal("14160"), null);
        assertThatThrownBy(() -> service.refund(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("supervisor");
        verify(stockMoveService, never()).post(any());
    }

    @Test
    void refund_aboveThreshold_supervisorMissingPermission_403() {
        PosSale original = persistedSale("TILL-1-R-P", "op-ref-p");
        when(sales.findById(original.getId())).thenReturn(Optional.of(original));
        when(lines.findByPosSaleIdOrderByLineNoAsc(original.getId()))
            .thenReturn(List.of(originalLine(original.getId(), new BigDecimal("120"), new BigDecimal("75"))));
        BusinessDay day = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(day);
        when(permissions.resolve(77L, COMPANY_ID, null)).thenReturn(java.util.Set.of());

        Long origId = original.getId();
        var req = refundRequest("TILL-1-R-P-REF", "op-ref-pp",
            origId, new BigDecimal("120"), new BigDecimal("100"), new BigDecimal("14160"), 77L);
        assertThatThrownBy(() -> service.refund(req))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
            .hasMessageContaining(PosSaleServiceImpl.REFUND_APPROVE_PERMISSION);
    }

    @Test
    void refund_aboveThreshold_approvedSupervisor_succeeds() {
        PosSale original = persistedSale("TILL-1-R-OK", "op-ref-ok");
        when(sales.findById(original.getId())).thenReturn(Optional.of(original));
        when(lines.findByPosSaleIdOrderByLineNoAsc(original.getId()))
            .thenReturn(List.of(originalLine(original.getId(), new BigDecimal("120"), new BigDecimal("75"))));
        BusinessDay day = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(day);
        when(permissions.resolve(77L, COMPANY_ID, null))
            .thenReturn(java.util.Set.of(PosSaleServiceImpl.REFUND_APPROVE_PERMISSION));

        PosSaleDto dto = service.refund(refundRequest("TILL-1-R-OK-REF", "op-ref-okok",
            original.getId(), new BigDecimal("120"), new BigDecimal("100"),
            new BigDecimal("14160"), 77L));

        assertThat(dto.kind()).isEqualTo(com.orbix.engine.modules.pos.domain.enums.PosSaleKind.REFUND);
        assertThat(dto.totalAmount()).isEqualByComparingTo("14160");
        assertThat(dto.supervisorId()).isEqualTo(77L);
    }

    @Test
    void refund_alreadyVoidedOriginal_isRejected() {
        PosSale original = persistedSale("TILL-1-R-V", "op-ref-v");
        original.voidSale("already voided", ACTOR_ID);
        when(sales.findById(original.getId())).thenReturn(Optional.of(original));

        Long origId = original.getId();
        var req = refundRequest("TILL-1-R-V-REF", "op-ref-vv",
            origId, new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("118"), null);
        assertThatThrownBy(() -> service.refund(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("VOIDED");
    }

    @Test
    void voidSale_batchTrackedItem_rejected() {
        Item batched = new Item(COMPANY_ID, "MILK", "Milk", ItemType.SELLABLE, 10L, UOM_ID, VAT_GROUP_ID, ACTOR_ID);
        batched.setId(ITEM_ID);
        batched.setBatchTracked(true);
        when(items.findById(ITEM_ID)).thenReturn(Optional.of(batched));

        PosSale sale = persistedSale("TILL-1-VB", "op-vb");
        when(sales.findById(sale.getId())).thenReturn(Optional.of(sale));
        PosSaleLine line = new PosSaleLine(sale.getId(), 1, ITEM_ID, UOM_ID,
            new BigDecimal("1"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
            VAT_GROUP_ID, BigDecimal.ZERO, new BigDecimal("100"));
        line.setId(nextId.getAndIncrement());
        when(lines.findByPosSaleIdOrderByLineNoAsc(sale.getId())).thenReturn(List.of(line));

        BusinessDay day = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(day);

        Long id = sale.getId();
        var req = new com.orbix.engine.modules.pos.domain.dto.VoidPosSaleRequestDto("damaged");
        assertThatThrownBy(() -> service.voidSale(id, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("batch-tracked");
    }

    // ---- F5.6: FX tender ----------------------------------------------------

    private com.orbix.engine.modules.admin.domain.entity.FxRate fxRateUsdToTzs(BigDecimal rate) {
        com.orbix.engine.modules.admin.domain.entity.FxRate fx =
            new com.orbix.engine.modules.admin.domain.entity.FxRate(
                "USD", "TZS", rate, Instant.parse("2026-05-13T08:00:00Z"), ACTOR_ID);
        fx.setId(9001L);
        return fx;
    }

    private PostPosSaleRequestDto fxSaleRequest(String number, String opId, BigDecimal qty,
                                                BigDecimal price, BigDecimal tender, String currency) {
        return new PostPosSaleRequestDto(
            number, opId, SESSION_ID, SECTION_ID, CUSTOMER_ID, null, null,
            Instant.parse("2026-05-13T10:00:00Z"), null,
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID, qty, price, null, VAT_GROUP_ID)),
            List.of(new PostPosSaleRequestDto.Payment(PosPaymentMethod.CASH, tender, currency,
                null, null, null)),
            null
        );
    }

    @Test
    void post_fxTender_convertsToFunctionalAndStoresSnapshot() {
        when(balances.findById(any(ItemBranchBalanceId.class)))
            .thenReturn(Optional.of(balance(new BigDecimal("75"))));
        when(tillCurrencies.existsByIdTillIdAndIdCurrencyCode(TILL_ID, "USD")).thenReturn(true);
        when(fxRates.findMostRecent(eq("USD"), eq("TZS"), any(Instant.class)))
            .thenReturn(Optional.of(fxRateUsdToTzs(new BigDecimal("2500"))));

        // Total in TZS: qty 1 * price 100000 + 18% VAT = 118000.
        // Tender 47.20 USD * 2500 = 118000.
        PosSaleDto dto = service.post(fxSaleRequest("TILL-1-FX", "op-fx",
            new BigDecimal("1"), new BigDecimal("100000"), new BigDecimal("47.20"), "USD"));

        assertThat(dto.totalAmount()).isEqualByComparingTo("118000");
        assertThat(dto.tenderedAmount()).isEqualByComparingTo("118000");
        assertThat(dto.changeAmount()).isEqualByComparingTo("0");
        assertThat(dto.payments()).hasSize(1);
        var pay = dto.payments().get(0);
        assertThat(pay.tenderCurrency()).isEqualTo("USD");
        assertThat(pay.tenderAmount()).isEqualByComparingTo("47.20");
        assertThat(pay.fxRateSnapshot()).isEqualByComparingTo("2500");
        assertThat(pay.amount()).isEqualByComparingTo("118000");
    }

    @Test
    void post_fxTender_currencyNotAcceptedByTill_isRejected() {
        when(tillCurrencies.existsByIdTillIdAndIdCurrencyCode(TILL_ID, "USD")).thenReturn(false);

        PostPosSaleRequestDto req = fxSaleRequest("TILL-1-FX-N", "op-fx-n",
            new BigDecimal("1"), new BigDecimal("100000"), new BigDecimal("47.20"), "USD");
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not accept tender currency USD");
        verify(sales, never()).save(any());
    }

    @Test
    void post_fxTender_noRateQuoted_isRejected() {
        when(tillCurrencies.existsByIdTillIdAndIdCurrencyCode(TILL_ID, "USD")).thenReturn(true);
        when(fxRates.findMostRecent(eq("USD"), eq("TZS"), any(Instant.class)))
            .thenReturn(Optional.empty());

        PostPosSaleRequestDto req = fxSaleRequest("TILL-1-FX-R", "op-fx-r",
            new BigDecimal("1"), new BigDecimal("100000"), new BigDecimal("47.20"), "USD");
        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No FX rate quoted for USD");
        verify(sales, never()).save(any());
    }

    @Test
    void post_functionalCurrencyExplicit_skipsFxLookup() {
        when(balances.findById(any(ItemBranchBalanceId.class)))
            .thenReturn(Optional.of(balance(new BigDecimal("75"))));

        // Explicitly send tenderCurrency = TZS — service uses rate 1, no FxRate lookup.
        PostPosSaleRequestDto req = fxSaleRequest("TILL-1-FX-F", "op-fx-f",
            new BigDecimal("1"), new BigDecimal("100"), new BigDecimal("118"), "TZS");
        PosSaleDto dto = service.post(req);
        assertThat(dto.payments().get(0).tenderCurrency()).isEqualTo("TZS");
        assertThat(dto.payments().get(0).fxRateSnapshot()).isEqualByComparingTo("1");
        assertThat(dto.payments().get(0).tenderAmount()).isEqualByComparingTo("118");
        verify(fxRates, never()).findMostRecent(any(), any(), any());
    }

    @Test
    void post_mixedFxAndFunctional_sumsBothAfterConversion() {
        when(balances.findById(any(ItemBranchBalanceId.class)))
            .thenReturn(Optional.of(balance(new BigDecimal("75"))));
        when(tillCurrencies.existsByIdTillIdAndIdCurrencyCode(TILL_ID, "USD")).thenReturn(true);
        when(fxRates.findMostRecent(eq("USD"), eq("TZS"), any(Instant.class)))
            .thenReturn(Optional.of(fxRateUsdToTzs(new BigDecimal("2500"))));

        // Total TZS 236; pay 100 TZS cash + 0.0544 USD * 2500 = 136 TZS — sums to 236.
        PostPosSaleRequestDto req = new PostPosSaleRequestDto(
            "TILL-1-FX-MIX", "op-fx-mix", SESSION_ID, SECTION_ID, CUSTOMER_ID, null, null,
            Instant.parse("2026-05-13T10:00:00Z"), null,
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID,
                new BigDecimal("2"), new BigDecimal("100"), null, VAT_GROUP_ID)),
            List.of(
                new PostPosSaleRequestDto.Payment(PosPaymentMethod.CASH, new BigDecimal("100"),
                    null, null, null, null),
                new PostPosSaleRequestDto.Payment(PosPaymentMethod.CASH, new BigDecimal("0.0544"),
                    "USD", null, null, null)
            ),
            null
        );
        PosSaleDto dto = service.post(req);
        assertThat(dto.tenderedAmount()).isEqualByComparingTo("236");
        assertThat(dto.changeAmount()).isEqualByComparingTo("0");
        assertThat(dto.payments()).hasSize(2);
    }

    // ===== F5.7 — Gift card tender =====

    @Test
    void post_giftCardTender_callsRedeemAndSkipsCashEntry() {
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
            .thenReturn(Optional.of(balance(new BigDecimal("75"))));

        // total 236 paid entirely via gift card → no cash side, redeem called.
        PostPosSaleRequestDto req = new PostPosSaleRequestDto(
            "TILL-1-GC1", "op-gc1", SESSION_ID, SECTION_ID, CUSTOMER_ID, null, null,
            Instant.parse("2026-05-13T10:00:00Z"), null,
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID,
                new BigDecimal("2"), new BigDecimal("100"), null, VAT_GROUP_ID)),
            List.of(new PostPosSaleRequestDto.Payment(PosPaymentMethod.GIFT_CARD,
                new BigDecimal("236"), null, "GC-CODE-1", null, null)),
            null
        );

        service.post(req);

        verify(giftCards).redeem(eq("GC-CODE-1"),
            org.mockito.ArgumentMatchers.argThat(r ->
                r.amount().compareTo(new BigDecimal("236")) == 0
                && "PosPayment".equals(r.refDocType())
                && r.refDocId() != null));
        // No cash entry for GIFT_CARD tender.
        verify(cashLedger, never()).post(any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void post_mixedCashAndGiftCard_postsCashEntryAndRedeem() {
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
            .thenReturn(Optional.of(balance(new BigDecimal("75"))));

        // total 236; pay 100 cash + 136 gift card.
        PostPosSaleRequestDto req = new PostPosSaleRequestDto(
            "TILL-1-GC2", "op-gc2", SESSION_ID, SECTION_ID, CUSTOMER_ID, null, null,
            Instant.parse("2026-05-13T10:00:00Z"), null,
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID,
                new BigDecimal("2"), new BigDecimal("100"), null, VAT_GROUP_ID)),
            List.of(
                new PostPosSaleRequestDto.Payment(PosPaymentMethod.CASH,
                    new BigDecimal("100"), null, null, null, null),
                new PostPosSaleRequestDto.Payment(PosPaymentMethod.GIFT_CARD,
                    new BigDecimal("136"), null, "GC-CODE-2", null, null)
            ),
            null
        );

        service.post(req);

        verify(giftCards).redeem(eq("GC-CODE-2"),
            org.mockito.ArgumentMatchers.argThat(r ->
                r.amount().compareTo(new BigDecimal("136")) == 0));
        // Exactly one cash entry — for the cash leg only.
        verify(cashLedger).post(any(), any(), any(), any(),
            eq(com.orbix.engine.modules.cash.domain.enums.CashAccount.TILL),
            eq(com.orbix.engine.modules.cash.domain.enums.CashDirection.IN),
            eq(new BigDecimal("100")), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void post_giftCardWithoutReference_isRejected() {
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
            .thenReturn(Optional.of(balance(new BigDecimal("75"))));

        PostPosSaleRequestDto req = new PostPosSaleRequestDto(
            "TILL-1-GC3", "op-gc3", SESSION_ID, SECTION_ID, CUSTOMER_ID, null, null,
            Instant.parse("2026-05-13T10:00:00Z"), null,
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID,
                new BigDecimal("2"), new BigDecimal("100"), null, VAT_GROUP_ID)),
            // GIFT_CARD tender with no reference → reject.
            List.of(new PostPosSaleRequestDto.Payment(PosPaymentMethod.GIFT_CARD,
                new BigDecimal("236"), null, null, null, null)),
            null
        );

        assertThatThrownBy(() -> service.post(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("card code");
        verify(giftCards, never()).redeem(any(), any());
    }

    @Test
    void voidSale_creditsBackOriginalGiftCardPayments() {
        // Set up an existing POSTED sale with a GIFT_CARD payment.
        PosSale sale = new PosSale(
            "TILL-1-GC-V", "op-gc-v", SESSION_ID, TILL_ID, BRANCH_ID, COMPANY_ID,
            SECTION_ID, CUSTOMER_ID, ACTOR_ID, null,
            com.orbix.engine.modules.pos.domain.enums.PosSaleKind.SALE,
            Instant.parse("2026-05-13T10:00:00Z"), LocalDate.of(2026, 5, 13),
            new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("236"), new BigDecimal("236"), BigDecimal.ZERO, null);
        sale.setId(7777L);
        PosSaleLine line = new PosSaleLine(7777L, 1, ITEM_ID, UOM_ID,
            new BigDecimal("2"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
            VAT_GROUP_ID, new BigDecimal("36"), new BigDecimal("236"));
        line.setCostAmount(new BigDecimal("50"));
        PosPayment gcPayment = new PosPayment(7777L, PosPaymentMethod.GIFT_CARD,
            new BigDecimal("236"), "TZS", new BigDecimal("236"), BigDecimal.ONE,
            "GC-CODE-V", null, null);
        gcPayment.setId(7780L);

        when(sales.findById(7777L)).thenReturn(Optional.of(sale));
        when(lines.findByPosSaleIdOrderByLineNoAsc(7777L)).thenReturn(List.of(line));
        when(payments.findByPosSaleIdOrderByIdAsc(7777L)).thenReturn(List.of(gcPayment));
        when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenReturn(new com.orbix.engine.modules.day.domain.entity.BusinessDay(
                BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID));

        service.voidSale(7777L,
            new com.orbix.engine.modules.pos.domain.dto.VoidPosSaleRequestDto("Customer changed mind"));

        verify(giftCards).refundCredit(eq("GC-CODE-V"),
            org.mockito.ArgumentMatchers.argThat(r ->
                r.amount().compareTo(new BigDecimal("236")) == 0
                && "PosVoidPayment".equals(r.refDocType())
                && r.refDocId().equals(7780L)));
        // No cash refund — the original tender wasn't cash.
        verify(cashLedger, never()).post(any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any());
    }
}

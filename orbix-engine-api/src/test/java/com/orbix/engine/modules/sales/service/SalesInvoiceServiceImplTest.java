package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.SettingsService;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.party.domain.entity.Customer;
import com.orbix.engine.modules.party.repository.CustomerRepository;
import com.orbix.engine.modules.sales.domain.dto.CreateSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.dto.PostSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.dto.ReprintInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.dto.SalesInvoiceDto;
import com.orbix.engine.modules.sales.domain.dto.VoidSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.enums.ReprintReason;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoiceLine;
import com.orbix.engine.modules.sales.domain.enums.PaymentTerms;
import com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus;
import com.orbix.engine.modules.sales.repository.SalesInvoiceLineRepository;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import com.orbix.engine.modules.stock.domain.dto.BatchPickDto;
import com.orbix.engine.modules.stock.domain.dto.ItemBranchBalanceDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.service.StockBatchService;
import com.orbix.engine.modules.stock.service.StockMoveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
class SalesInvoiceServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long CUSTOMER_ID = 540L;
    private static final Long ITEM_ID = 8801L;
    private static final Long UOM_ID = 1L;
    private static final Long VAT_GROUP_ID = 2L;
    private static final Long PRICE_LIST_ID = 5L;
    private static final Long ACTOR_ID = 4L;
    private static final Long APPROVER_ID = 9L;

    @Mock private SalesInvoiceRepository invoices;
    @Mock private SalesInvoiceLineRepository lines;
    @Mock private ItemRepository items;
    @Mock private VatGroupRepository vatGroups;
    @Mock private CustomerRepository customers;
    @Mock private StockMoveService stockMoveService;
    @Mock private StockBatchService stockBatchService;
    @Mock private DayGuard dayGuard;
    @Mock private PermissionResolverService permissions;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;
    @Mock private SettingsService settings;

    @InjectMocks private SalesInvoiceServiceImpl service;

    private final AtomicLong nextId = new AtomicLong(5000);

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(settings.getDecimal(SettingKey.SALES_DISCOUNT_THRESHOLD_PCT))
            .thenReturn(new BigDecimal("10"));

        Item item = new Item(COMPANY_ID, "SKU1", "Sugar", ItemType.SELLABLE, 10L, UOM_ID, VAT_GROUP_ID, ACTOR_ID);
        item.setId(ITEM_ID);
        lenient().when(items.findById(ITEM_ID)).thenReturn(Optional.of(item));

        VatGroup vat = new VatGroup(COMPANY_ID, "STD", "Standard", new BigDecimal("0.18"),
            LocalDate.of(2020, 1, 1), true, ACTOR_ID);
        vat.setId(VAT_GROUP_ID);
        lenient().when(vatGroups.findById(VAT_GROUP_ID)).thenReturn(Optional.of(vat));

        Customer customer = new Customer(CUSTOMER_ID);
        customer.setCreditLimitAmount(new BigDecimal("10000"));
        customer.setCreditTermsDays(30);
        lenient().when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        lenient().when(invoices.save(any(SalesInvoice.class))).thenAnswer(inv -> {
            SalesInvoice s = inv.getArgument(0);
            if (s.getId() == null) s.setId(nextId.getAndIncrement());
            return s;
        });
        lenient().when(lines.save(any(SalesInvoiceLine.class))).thenAnswer(inv -> {
            SalesInvoiceLine l = inv.getArgument(0);
            l.setId(nextId.getAndIncrement());
            return l;
        });
        lenient().when(lines.findBySalesInvoiceIdOrderByLineNoAsc(any()))
            .thenAnswer(inv -> List.<SalesInvoiceLine>of());
        lenient().when(invoices.sumOutstandingDebt(CUSTOMER_ID)).thenReturn(BigDecimal.ZERO);
    }

    private CreateSalesInvoiceRequestDto draft(String number, PaymentTerms terms,
                                               BigDecimal qty, BigDecimal price, BigDecimal discountPct,
                                               Long approver) {
        return new CreateSalesInvoiceRequestDto(
            number, BRANCH_ID, CUSTOMER_ID, null,
            LocalDate.of(2026, 5, 13), terms == PaymentTerms.CREDIT ? LocalDate.of(2026, 6, 12) : null,
            terms, "TZS", PRICE_LIST_ID, approver, null, "test",
            List.of(new CreateSalesInvoiceRequestDto.Line(
                ITEM_ID, UOM_ID, qty, price, discountPct, VAT_GROUP_ID
            ))
        );
    }

    @Test
    void createDraft_capturesLines_andEmitsCreated() {
        SalesInvoiceDto dto = service.createDraft(draft("SI-1", PaymentTerms.CASH,
            new BigDecimal("10"), new BigDecimal("100"), BigDecimal.ZERO, null));

        // 10 * 100 = 1000 subtotal; tax 18% = 180; total 1180
        assertThat(dto.subtotalAmount()).isEqualByComparingTo("1000");
        assertThat(dto.taxAmount()).isEqualByComparingTo("180");
        assertThat(dto.totalAmount()).isEqualByComparingTo("1180");
        assertThat(dto.status()).isEqualTo(SalesInvoiceStatus.DRAFT);
        verify(events).publish(eq("SalesInvoiceCreated.v1"), any(), any(), any());
    }

    @Test
    void createDraft_creditExceedingLimit_isAllowed_gateFireOnPost() {
        // Slice C design intent: drafts are NEVER rejected on credit limit.
        // The gate fires at POST time (with SALES_INVOICE.OVERRIDE_CREDIT as
        // the off-ramp). Drafts are a negotiation surface — they can sit at
        // any total without committing the customer to the debt.
        Customer customer = new Customer(CUSTOMER_ID);
        customer.setCreditLimitAmount(new BigDecimal("500"));
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        CreateSalesInvoiceRequestDto request = draft("SI-CR", PaymentTerms.CREDIT,
            new BigDecimal("10"), new BigDecimal("100"), BigDecimal.ZERO, null);
        SalesInvoiceDto dto = service.createDraft(request);

        assertThat(dto.status()).isEqualTo(SalesInvoiceStatus.DRAFT);
        assertThat(dto.totalAmount()).isEqualByComparingTo("1180");
    }

    @Test
    void createDraft_creditWithZeroLimit_isAllowed_gateFireOnPost() {
        // Same intent: a customer with zero credit limit can still have a
        // draft created against them. POST is where the gate runs.
        Customer customer = new Customer(CUSTOMER_ID);
        customer.setCreditLimitAmount(BigDecimal.ZERO);
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        CreateSalesInvoiceRequestDto request = draft("SI-NOC", PaymentTerms.CREDIT,
            new BigDecimal("1"), new BigDecimal("100"), BigDecimal.ZERO, null);
        SalesInvoiceDto dto = service.createDraft(request);

        assertThat(dto.status()).isEqualTo(SalesInvoiceStatus.DRAFT);
    }

    @Test
    void createDraft_discountAboveThresholdRequiresApprover() {
        CreateSalesInvoiceRequestDto request = draft("SI-DISC", PaymentTerms.CASH,
            new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("15"), null);
        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("authoriser");
    }

    @Test
    void createDraft_discountApproverMissingPermission_403() {
        when(permissions.resolve(APPROVER_ID, COMPANY_ID, null)).thenReturn(Set.of());

        CreateSalesInvoiceRequestDto request = draft("SI-DISC2", PaymentTerms.CASH,
            new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("15"), APPROVER_ID);
        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
            .hasMessageContaining(SalesInvoiceServiceImpl.DISCOUNT_APPROVE_PERMISSION);
    }

    @Test
    void createDraft_discountApproverWithPermission_succeeds() {
        when(permissions.resolve(APPROVER_ID, COMPANY_ID, null))
            .thenReturn(Set.of(SalesInvoiceServiceImpl.DISCOUNT_APPROVE_PERMISSION));

        SalesInvoiceDto dto = service.createDraft(draft("SI-OK", PaymentTerms.CASH,
            new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("15"), APPROVER_ID));

        assertThat(dto.status()).isEqualTo(SalesInvoiceStatus.DRAFT);
    }

    @Test
    void createDraft_belowMinSellPrice_rejected() {
        Item item = new Item(COMPANY_ID, "SKU1", "Sugar", ItemType.SELLABLE, 10L, UOM_ID, VAT_GROUP_ID, ACTOR_ID);
        item.setId(ITEM_ID);
        item.setMinSellPrice(new BigDecimal("90"));
        when(items.findById(ITEM_ID)).thenReturn(Optional.of(item));

        // unit 100 with 15% discount + approver → net 85 < 90 → reject
        when(permissions.resolve(APPROVER_ID, COMPANY_ID, null))
            .thenReturn(Set.of(SalesInvoiceServiceImpl.DISCOUNT_APPROVE_PERMISSION));

        CreateSalesInvoiceRequestDto request = draft("SI-MSP", PaymentTerms.CASH,
            new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("15"), APPROVER_ID);
        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("min sell price");
    }

    @Test
    void post_writesStockMovesAndEmitsPosted() {
        SalesInvoice invoice = postableInvoice("SI-P", PaymentTerms.CASH, new BigDecimal("1180"));
        SalesInvoiceLine line = lineRow(invoice, new BigDecimal("10"), new BigDecimal("100"));
        when(invoices.findByUid(invoice.getUid())).thenReturn(Optional.of(invoice));
        when(lines.findBySalesInvoiceIdOrderByLineNoAsc(invoice.getId())).thenReturn(List.of(line));
        when(stockMoveService.findBalance(ITEM_ID, BRANCH_ID))
            .thenReturn(Optional.of(balanceDto(new BigDecimal("75"))));
        BusinessDay day = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(day);

        SalesInvoiceDto dto = service.post(invoice.getUid(), PostSalesInvoiceRequestDto.empty());

        assertThat(dto.status()).isEqualTo(SalesInvoiceStatus.POSTED);
        assertThat(dto.postedBusinessDate()).isEqualTo(LocalDate.of(2026, 5, 13));
        assertThat(line.getCostAmount()).isEqualByComparingTo("75");

        ArgumentCaptor<PostStockMoveRequestDto> postCaptor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(postCaptor.capture());
        PostStockMoveRequestDto move = postCaptor.getValue();
        assertThat(move.moveType()).isEqualTo(StockMoveType.SALE);
        assertThat(move.qty()).isEqualByComparingTo("-10");
        verify(events).publish(eq("SalesInvoicePosted.v1"), any(), any(), any());
    }

    @Test
    void post_batchTrackedItem_drainsFefoAndWritesOneMovePerPick() {
        Item batched = new Item(COMPANY_ID, "MILK", "Milk", ItemType.SELLABLE, 10L, UOM_ID, VAT_GROUP_ID, ACTOR_ID);
        batched.setId(ITEM_ID);
        batched.setBatchTracked(true);
        when(items.findById(ITEM_ID)).thenReturn(Optional.of(batched));

        SalesInvoice invoice = postableInvoice("SI-B", PaymentTerms.CASH, new BigDecimal("1180"));
        SalesInvoiceLine line = lineRow(invoice, new BigDecimal("10"), new BigDecimal("100"));
        when(invoices.findByUid(invoice.getUid())).thenReturn(Optional.of(invoice));
        when(lines.findBySalesInvoiceIdOrderByLineNoAsc(invoice.getId())).thenReturn(List.of(line));
        when(stockBatchService.drainFefo(ITEM_ID, BRANCH_ID, new BigDecimal("10")))
            .thenReturn(List.of(
                new BatchPickDto(101L, "B-1", new BigDecimal("4"), new BigDecimal("70")),
                new BatchPickDto(102L, "B-2", new BigDecimal("6"), new BigDecimal("80"))
            ));
        BusinessDay day = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(day);

        service.post(invoice.getUid(), PostSalesInvoiceRequestDto.empty());

        // 4*70 + 6*80 = 280 + 480 = 760; 760 / 10 = 76 weighted avg
        assertThat(line.getCostAmount()).isEqualByComparingTo("76");

        ArgumentCaptor<PostStockMoveRequestDto> postCaptor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService, org.mockito.Mockito.times(2)).post(postCaptor.capture());
        List<PostStockMoveRequestDto> moves = postCaptor.getAllValues();
        assertThat(moves).extracting(PostStockMoveRequestDto::batchId).containsExactly(101L, 102L);
    }

    @Test
    void post_requiresOpenBusinessDay() {
        SalesInvoice invoice = postableInvoice("SI-DAY", PaymentTerms.CASH, new BigDecimal("100"));
        when(invoices.findByUid(invoice.getUid())).thenReturn(Optional.of(invoice));
        when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenThrow(new IllegalStateException("No open business day"));

        String uid = invoice.getUid();
        PostSalesInvoiceRequestDto empty = PostSalesInvoiceRequestDto.empty();
        assertThatThrownBy(() -> service.post(uid, empty))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void voidInvoice_sameDay_writesCompensatingMovesAndFlipsToVoided() {
        SalesInvoice invoice = postableInvoice("SI-V", PaymentTerms.CASH, new BigDecimal("1180"));
        invoice.post(LocalDate.of(2026, 5, 13), ACTOR_ID);
        SalesInvoiceLine line = lineRow(invoice, new BigDecimal("10"), new BigDecimal("100"));
        line.setCostAmount(new BigDecimal("75"));
        when(invoices.findByUid(invoice.getUid())).thenReturn(Optional.of(invoice));
        when(lines.findBySalesInvoiceIdOrderByLineNoAsc(invoice.getId())).thenReturn(List.of(line));
        BusinessDay day = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(day);

        SalesInvoiceDto dto = service.voidInvoice(invoice.getUid(),
            new VoidSalesInvoiceRequestDto("operator error"));

        assertThat(dto.status()).isEqualTo(SalesInvoiceStatus.VOIDED);
        ArgumentCaptor<PostStockMoveRequestDto> captor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(captor.capture());
        PostStockMoveRequestDto move = captor.getValue();
        assertThat(move.moveType()).isEqualTo(StockMoveType.RETURN_IN);
        assertThat(move.qty()).isEqualByComparingTo("10");
        assertThat(move.unitCost()).isEqualByComparingTo("75");
        verify(events).publish(eq("SalesInvoiceVoided.v1"), any(), any(), any());
    }

    @Test
    void voidInvoice_differentBusinessDay_isRejected() {
        SalesInvoice invoice = postableInvoice("SI-VD", PaymentTerms.CASH, new BigDecimal("100"));
        invoice.post(LocalDate.of(2026, 5, 12), ACTOR_ID);
        when(invoices.findByUid(invoice.getUid())).thenReturn(Optional.of(invoice));
        BusinessDay day = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(day);

        String uid = invoice.getUid();
        VoidSalesInvoiceRequestDto req = new VoidSalesInvoiceRequestDto("late");
        assertThatThrownBy(() -> service.voidInvoice(uid, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("same business day");
        verify(stockMoveService, never()).post(any());
    }

    @Test
    void voidInvoice_batchTrackedItem_isRejected() {
        Item batched = new Item(COMPANY_ID, "MILK", "Milk", ItemType.SELLABLE, 10L, UOM_ID, VAT_GROUP_ID, ACTOR_ID);
        batched.setId(ITEM_ID);
        batched.setBatchTracked(true);
        when(items.findById(ITEM_ID)).thenReturn(Optional.of(batched));

        SalesInvoice invoice = postableInvoice("SI-VB", PaymentTerms.CASH, new BigDecimal("100"));
        invoice.post(LocalDate.of(2026, 5, 13), ACTOR_ID);
        SalesInvoiceLine line = lineRow(invoice, new BigDecimal("1"), new BigDecimal("100"));
        when(invoices.findByUid(invoice.getUid())).thenReturn(Optional.of(invoice));
        when(lines.findBySalesInvoiceIdOrderByLineNoAsc(invoice.getId())).thenReturn(List.of(line));
        BusinessDay day = new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID);
        when(dayGuard.requireOpenDay(BRANCH_ID)).thenReturn(day);

        String uid = invoice.getUid();
        VoidSalesInvoiceRequestDto req = new VoidSalesInvoiceRequestDto("test");
        assertThatThrownBy(() -> service.voidInvoice(uid, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("batch-tracked");
    }

    @Test
    void cancel_fromDraft_succeeds() {
        SalesInvoice invoice = postableInvoice("SI-CXL", PaymentTerms.CASH, new BigDecimal("100"));
        when(invoices.findByUid(invoice.getUid())).thenReturn(Optional.of(invoice));

        SalesInvoiceDto dto = service.cancel(invoice.getUid());

        assertThat(dto.status()).isEqualTo(SalesInvoiceStatus.CANCELLED);
        verify(events).publish(eq("SalesInvoiceCancelled.v1"), any(), any(), any());
    }

    // ---------------------------------------------------------------------
    // Slice C — credit-limit override at POST (GAP 3.A / 7.A)
    // ---------------------------------------------------------------------

    @Test
    void post_creditExceedingLimit_withoutOverridePerm_isRejected() {
        Customer low = new Customer(CUSTOMER_ID);
        low.setCreditLimitAmount(new BigDecimal("500"));
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(low));

        SalesInvoice invoice = postableInvoice("SI-OVR-NP", PaymentTerms.CREDIT, new BigDecimal("1180"));
        when(invoices.findByUid(invoice.getUid())).thenReturn(Optional.of(invoice));
        when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenReturn(new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID));
        when(permissions.resolve(ACTOR_ID, COMPANY_ID, BRANCH_ID)).thenReturn(Set.of());

        String uid = invoice.getUid();
        PostSalesInvoiceRequestDto withReason = new PostSalesInvoiceRequestDto("manager override");
        assertThatThrownBy(() -> service.post(uid, withReason))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("credit limit");
        verify(stockMoveService, never()).post(any());
    }

    @Test
    void post_creditExceedingLimit_withOverridePermAndReason_succeeds() {
        Customer low = new Customer(CUSTOMER_ID);
        low.setCreditLimitAmount(new BigDecimal("500"));
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(low));
        when(permissions.resolve(ACTOR_ID, COMPANY_ID, BRANCH_ID))
            .thenReturn(Set.of(SalesInvoiceServiceImpl.OVERRIDE_CREDIT_PERMISSION));

        SalesInvoice invoice = postableInvoice("SI-OVR", PaymentTerms.CREDIT, new BigDecimal("1180"));
        SalesInvoiceLine line = lineRow(invoice, new BigDecimal("10"), new BigDecimal("100"));
        when(invoices.findByUid(invoice.getUid())).thenReturn(Optional.of(invoice));
        when(lines.findBySalesInvoiceIdOrderByLineNoAsc(invoice.getId())).thenReturn(List.of(line));
        when(stockMoveService.findBalance(ITEM_ID, BRANCH_ID))
            .thenReturn(Optional.of(balanceDto(new BigDecimal("75"))));
        when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenReturn(new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID));

        SalesInvoiceDto dto = service.post(invoice.getUid(),
            new PostSalesInvoiceRequestDto("Approved by manager"));

        assertThat(dto.status()).isEqualTo(SalesInvoiceStatus.POSTED);
        assertThat(dto.creditOverride()).isTrue();
        assertThat(dto.creditOverrideBy()).isEqualTo(ACTOR_ID);
        assertThat(dto.creditOverrideReason()).isEqualTo("Approved by manager");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(events).publish(eq("SalesInvoicePosted.v1"), any(), any(), payloadCaptor.capture());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> payload = (java.util.Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload).containsEntry("creditOverride", true);
        assertThat(payload).containsEntry("creditOverrideReason", "Approved by manager");
        assertThat(payload).containsKey("lines");
    }

    @Test
    void post_creditExceedingLimit_withOverridePermButBlankReason_isRejected() {
        Customer low = new Customer(CUSTOMER_ID);
        low.setCreditLimitAmount(new BigDecimal("500"));
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(low));

        SalesInvoice invoice = postableInvoice("SI-OVR-NR", PaymentTerms.CREDIT, new BigDecimal("1180"));
        when(invoices.findByUid(invoice.getUid())).thenReturn(Optional.of(invoice));
        when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenReturn(new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID));
        // Reason absent → override not exercised even if the perm is present.
        String uid = invoice.getUid();
        PostSalesInvoiceRequestDto blank = new PostSalesInvoiceRequestDto("   ");
        assertThatThrownBy(() -> service.post(uid, blank))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("credit limit");
    }

    @Test
    void post_creditExceedingLimit_zeroLimitCustomer_isRejected_evenWithOverridePerm() {
        Customer zero = new Customer(CUSTOMER_ID);
        zero.setCreditLimitAmount(BigDecimal.ZERO);
        when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(zero));
        when(permissions.resolve(ACTOR_ID, COMPANY_ID, BRANCH_ID))
            .thenReturn(Set.of(SalesInvoiceServiceImpl.OVERRIDE_CREDIT_PERMISSION));

        SalesInvoice invoice = postableInvoice("SI-OVR-Z", PaymentTerms.CREDIT, new BigDecimal("100"));
        when(invoices.findByUid(invoice.getUid())).thenReturn(Optional.of(invoice));
        when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenReturn(new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID));

        String uid = invoice.getUid();
        PostSalesInvoiceRequestDto withReason = new PostSalesInvoiceRequestDto("anyway");
        assertThatThrownBy(() -> service.post(uid, withReason))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no credit limit");
    }

    // ---------------------------------------------------------------------
    // Slice C — void event widening (GAP 9 / e2e contract)
    // ---------------------------------------------------------------------

    @Test
    void voidInvoice_emitsCompensatingPayload() {
        SalesInvoice invoice = postableInvoice("SI-VWP", PaymentTerms.CASH, new BigDecimal("1180"));
        invoice.post(LocalDate.of(2026, 5, 13), ACTOR_ID);
        SalesInvoiceLine line = lineRow(invoice, new BigDecimal("10"), new BigDecimal("100"));
        line.setCostAmount(new BigDecimal("75"));
        when(invoices.findByUid(invoice.getUid())).thenReturn(Optional.of(invoice));
        when(lines.findBySalesInvoiceIdOrderByLineNoAsc(invoice.getId())).thenReturn(List.of(line));
        when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenReturn(new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 13), ACTOR_ID));

        service.voidInvoice(invoice.getUid(), new VoidSalesInvoiceRequestDto("operator error"));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publish(eq("SalesInvoiceVoided.v1"), any(), any(), captor.capture());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> payload = (java.util.Map<String, Object>) captor.getValue();
        assertThat(payload).containsEntry("compensating", true);
        assertThat(payload).containsEntry("reason", "operator error");
        assertThat(payload).containsEntry("priorStatus", "POSTED");
        assertThat(payload).containsKey("voidedAt");
        assertThat(payload).containsEntry("voidedBy", ACTOR_ID);
    }

    // ---------------------------------------------------------------------
    // Slice C — reprint audit (GAP 3.B / 9.B)
    // ---------------------------------------------------------------------

    @Test
    void reprint_postedInvoice_incrementsCount_andEmitsEvent() {
        SalesInvoice invoice = postableInvoice("SI-RPR", PaymentTerms.CASH, new BigDecimal("1180"));
        invoice.post(LocalDate.of(2026, 5, 13), ACTOR_ID);
        when(invoices.findByUid(invoice.getUid())).thenReturn(Optional.of(invoice));

        SalesInvoiceDto dto = service.reprint(invoice.getUid(),
            new ReprintInvoiceRequestDto(ReprintReason.DUPLICATE, "Customer lost original"));

        assertThat(dto.reprintCount()).isEqualTo(1);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publish(eq("SalesInvoiceReprinted.v1"), any(), any(), captor.capture());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> payload = (java.util.Map<String, Object>) captor.getValue();
        assertThat(payload).containsEntry("reason", "DUPLICATE");
        assertThat(payload).containsEntry("notes", "Customer lost original");
        assertThat(payload).containsEntry("reprintCount", 1);
    }

    @Test
    void reprint_draftInvoice_isRejected() {
        SalesInvoice invoice = postableInvoice("SI-RPRD", PaymentTerms.CASH, new BigDecimal("100"));
        when(invoices.findByUid(invoice.getUid())).thenReturn(Optional.of(invoice));

        String uid = invoice.getUid();
        ReprintInvoiceRequestDto req = new ReprintInvoiceRequestDto(ReprintReason.OTHER, null);
        assertThatThrownBy(() -> service.reprint(uid, req))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot reprint");
    }

    private SalesInvoice postableInvoice(String number, PaymentTerms terms, BigDecimal total) {
        SalesInvoice invoice = new SalesInvoice(number, COMPANY_ID, BRANCH_ID, CUSTOMER_ID, null,
            LocalDate.of(2026, 5, 13), null, terms, "TZS", PRICE_LIST_ID,
            null, null, ACTOR_ID);
        invoice.setId(nextId.getAndIncrement());
        ReflectionTestUtils.setField(invoice, "uid", UidGenerator.next());
        invoice.rollUpTotals(total, BigDecimal.ZERO, BigDecimal.ZERO);
        return invoice;
    }

    private SalesInvoiceLine lineRow(SalesInvoice invoice, BigDecimal qty, BigDecimal unitPrice) {
        SalesInvoiceLine line = new SalesInvoiceLine(invoice.getId(), 1, ITEM_ID, UOM_ID,
            qty, unitPrice, BigDecimal.ZERO, BigDecimal.ZERO, VAT_GROUP_ID,
            BigDecimal.ZERO, qty.multiply(unitPrice));
        line.setId(nextId.getAndIncrement());
        return line;
    }

    private ItemBranchBalanceDto balanceDto(BigDecimal avgCost) {
        return new ItemBranchBalanceDto(
            ITEM_ID, BRANCH_ID,
            new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
            avgCost, avgCost,
            null, null, null, null);
    }
}

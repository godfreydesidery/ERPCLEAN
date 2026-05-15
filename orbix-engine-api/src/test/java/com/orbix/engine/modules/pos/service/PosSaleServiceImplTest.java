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
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
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
    @Mock private SectionRepository sections;
    @Mock private ItemRepository items;
    @Mock private VatGroupRepository vatGroups;
    @Mock private CustomerRepository customers;
    @Mock private ItemBranchBalanceRepository balances;
    @Mock private StockMoveService stockMoveService;
    @Mock private StockBatchService stockBatchService;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;

    @InjectMocks private PosSaleServiceImpl service;

    private final AtomicLong nextId = new AtomicLong(9000);

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);

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
            number, clientOpId, SESSION_ID, SECTION_ID, CUSTOMER_ID, null,
            Instant.parse("2026-05-13T10:00:00Z"),
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID, qty, price, null, VAT_GROUP_ID)),
            List.of(new PostPosSaleRequestDto.Payment(PosPaymentMethod.CASH, tender, null, null, null)),
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
            "TILL-1-M", "op-mix", SESSION_ID, SECTION_ID, CUSTOMER_ID, null,
            Instant.parse("2026-05-13T11:00:00Z"),
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID,
                new BigDecimal("1"), new BigDecimal("100"), null, VAT_GROUP_ID)),
            // total 118; cash 50 + card 68 = 118
            List.of(
                new PostPosSaleRequestDto.Payment(PosPaymentMethod.CASH, new BigDecimal("50"), null, null, null),
                new PostPosSaleRequestDto.Payment(PosPaymentMethod.CARD, new BigDecimal("68"), "AUTH-1", "T1", "1234")
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
            "TILL-1-P", "op-pay", SESSION_ID, SECTION_ID, CUSTOMER_ID, null,
            Instant.parse("2026-05-13T12:00:00Z"),
            List.of(new PostPosSaleRequestDto.Line(ITEM_ID, UOM_ID,
                new BigDecimal("1"), new BigDecimal("100"), null, VAT_GROUP_ID)),
            List.of(new PostPosSaleRequestDto.Payment(PosPaymentMethod.CARD,
                new BigDecimal("118"), "AUTH-9", "POS-T", "9999")),
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
}

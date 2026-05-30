package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.domain.enums.Permissions;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockMoveDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.entity.StockMove;
import com.orbix.engine.modules.stock.domain.enums.StockMoveDirection;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.procurement.repository.GrnRepository;
import com.orbix.engine.modules.procurement.repository.VendorReturnRepository;
import com.orbix.engine.modules.sales.repository.CustomerReturnRepository;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.procurement.domain.entity.Grn;
import com.orbix.engine.modules.stock.domain.dto.ItemBranchBalanceDto;
import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.repository.StockCountRepository;
import com.orbix.engine.modules.stock.repository.StockMoveRepository;
import com.orbix.engine.modules.stock.repository.StockTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockMoveServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long ITEM_ID = 8801L;
    private static final Long ACTOR_ID = 4L;

    @Mock private StockMoveRepository moves;
    @Mock private ItemBranchBalanceRepository balances;
    @Mock private ItemRepository items;
    @Mock private DayGuard dayGuard;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;
    @Mock private PermissionResolverService permissions;
    // doc-number repos
    @Mock private SalesInvoiceRepository salesInvoices;
    @Mock private GrnRepository grns;
    @Mock private CustomerReturnRepository customerReturns;
    @Mock private VendorReturnRepository vendorReturns;
    @Mock private StockCountRepository stockCounts;
    @Mock private StockTransferRepository stockTransfers;

    @InjectMocks private StockMoveServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        Item item = new Item(COMPANY_ID, "SKU1", "Sugar", ItemType.SELLABLE, 1L, 1L, 1L, ACTOR_ID);
        item.setId(ITEM_ID);
        lenient().when(items.findById(ITEM_ID)).thenReturn(Optional.of(item));
        lenient().when(moves.save(any(StockMove.class))).thenAnswer(inv -> {
            StockMove m = inv.getArgument(0);
            m.setId(1L);
            return m;
        });
        lenient().when(balances.save(any(ItemBranchBalance.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ItemBranchBalance balance(BigDecimal qtyOnHand, BigDecimal avgCost) {
        ItemBranchBalance b = new ItemBranchBalance(ITEM_ID, BRANCH_ID);
        b.setQtyOnHand(qtyOnHand);
        b.setAvgCost(avgCost);
        return b;
    }

    private static PostStockMoveRequestDto req(BigDecimal qty, BigDecimal unitCost, boolean oversell) {
        return new PostStockMoveRequestDto(ITEM_ID, BRANCH_ID, qty, unitCost,
            StockMoveType.ADJUSTMENT, "Adjustment", 99L, null, oversell);
    }

    private static PostStockMoveRequestDto reqWithBatch(BigDecimal qty, Long batchId) {
        return new PostStockMoveRequestDto(ITEM_ID, BRANCH_ID, qty, null,
            StockMoveType.EXPIRY_WRITE_OFF, "StockBatch", batchId, "test", false, batchId);
    }

    @Test
    void post_firstInbound_setsAvgCostToUnitCost() {
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID))).thenReturn(Optional.empty());

        StockMoveDto result = service.post(req(new BigDecimal("10"), new BigDecimal("100"), false));

        assertThat(result.direction()).isEqualTo(StockMoveDirection.IN);
        assertThat(result.costAmount()).isEqualByComparingTo("100");
        ArgumentCaptor<ItemBranchBalance> saved = ArgumentCaptor.forClass(ItemBranchBalance.class);
        verify(balances).save(saved.capture());
        assertThat(saved.getValue().getQtyOnHand()).isEqualByComparingTo("10");
        assertThat(saved.getValue().getAvgCost()).isEqualByComparingTo("100");
    }

    @Test
    void post_secondInbound_recomputesMovingAverage() {
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
            .thenReturn(Optional.of(balance(new BigDecimal("10"), new BigDecimal("100"))));

        service.post(req(new BigDecimal("10"), new BigDecimal("200"), false));

        ArgumentCaptor<ItemBranchBalance> saved = ArgumentCaptor.forClass(ItemBranchBalance.class);
        verify(balances).save(saved.capture());
        // (10*100 + 10*200) / 20 = 150
        assertThat(saved.getValue().getAvgCost()).isEqualByComparingTo("150.0000");
        assertThat(saved.getValue().getQtyOnHand()).isEqualByComparingTo("20");
    }

    @Test
    void post_outbound_consumesAtAvgCostAndReducesQty() {
        // Outbound path uses the pessimistic-lock finder (ISSUE-NFR-002 fix).
        when(balances.findByItemIdAndBranchIdForUpdate(ITEM_ID, BRANCH_ID))
            .thenReturn(Optional.of(balance(new BigDecimal("10"), new BigDecimal("150"))));

        StockMoveDto result = service.post(req(new BigDecimal("-4"), null, false));

        assertThat(result.direction()).isEqualTo(StockMoveDirection.OUT);
        assertThat(result.costAmount()).isEqualByComparingTo("150");
        ArgumentCaptor<ItemBranchBalance> saved = ArgumentCaptor.forClass(ItemBranchBalance.class);
        verify(balances).save(saved.capture());
        assertThat(saved.getValue().getQtyOnHand()).isEqualByComparingTo("6");
    }

    @Test
    void post_outboundBelowZero_isBlockedWithoutOverride() {
        when(balances.findByItemIdAndBranchIdForUpdate(ITEM_ID, BRANCH_ID))
            .thenReturn(Optional.of(balance(new BigDecimal("3"), new BigDecimal("150"))));

        assertThatThrownBy(() -> service.post(req(new BigDecimal("-5"), null, false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(Permissions.STOCK_OVERSELL);
        verify(moves, never()).save(any());
        verify(events).publish(eq("NegativeStockBlocked.v1"), any(), any(), any());
    }

    @Test
    void post_outboundBelowZero_isAllowedWithOverrideAndPermission() {
        when(balances.findByItemIdAndBranchIdForUpdate(ITEM_ID, BRANCH_ID))
            .thenReturn(Optional.of(balance(new BigDecimal("3"), new BigDecimal("150"))));
        when(permissions.resolve(ACTOR_ID, COMPANY_ID, BRANCH_ID))
            .thenReturn(Set.of(Permissions.STOCK_OVERSELL));

        service.post(req(new BigDecimal("-5"), null, true));

        ArgumentCaptor<ItemBranchBalance> saved = ArgumentCaptor.forClass(ItemBranchBalance.class);
        verify(balances).save(saved.capture());
        assertThat(saved.getValue().getQtyOnHand()).isEqualByComparingTo("-2");
        verify(events, never()).publish(eq("NegativeStockBlocked.v1"), any(), any(), any());
    }

    @Test
    void post_outboundBelowZero_withOverrideButNoPermission_isBlocked() {
        when(balances.findByItemIdAndBranchIdForUpdate(ITEM_ID, BRANCH_ID))
            .thenReturn(Optional.of(balance(new BigDecimal("3"), new BigDecimal("150"))));
        when(permissions.resolve(ACTOR_ID, COMPANY_ID, BRANCH_ID)).thenReturn(Set.of());

        assertThatThrownBy(() -> service.post(req(new BigDecimal("-5"), null, true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(Permissions.STOCK_OVERSELL);
        verify(moves, never()).save(any());
        verify(events).publish(eq("NegativeStockBlocked.v1"), any(), any(), any());
    }

    @Test
    void post_outboundNotNegative_doesNotConsultPermissionResolver() {
        when(balances.findByItemIdAndBranchIdForUpdate(ITEM_ID, BRANCH_ID))
            .thenReturn(Optional.of(balance(new BigDecimal("10"), new BigDecimal("150"))));

        // -4 leaves 6 on-hand; OVERSELL gate must not fire.
        service.post(req(new BigDecimal("-4"), null, true));

        verify(permissions, never()).resolve(any(), any(), any());
    }

    @Test
    void post_requiresOpenBusinessDay() {
        when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenThrow(new IllegalStateException("No open business day"));

        assertThatThrownBy(() -> service.post(req(new BigDecimal("10"), new BigDecimal("100"), false)))
            .isInstanceOf(IllegalStateException.class);
        verify(moves, never()).save(any());
    }

    @Test
    void post_itemFromAnotherCompany_throwsNotFound() {
        Item foreign = new Item(999L, "SKU9", "Foreign", ItemType.SELLABLE, 1L, 1L, 1L, ACTOR_ID);
        foreign.setId(ITEM_ID);
        when(items.findById(ITEM_ID)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.post(req(new BigDecimal("10"), new BigDecimal("100"), false)))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void post_emitsStockMovedAndBalanceUpdated() {
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID))).thenReturn(Optional.empty());

        service.post(req(new BigDecimal("10"), new BigDecimal("100"), false));

        verify(events).publish(eq("StockMoved.v1"), any(), any(), any());
        verify(events).publish(eq("BalanceUpdated.v1"), any(), any(), any());
    }

    // ---------------------------------------------------------------------
    // Slice F — listBalances filter flags (GAP 7.C)
    // ---------------------------------------------------------------------

    @Test
    void listBalances_noFlags_returnsAllRows() {
        ItemBranchBalance neg = balance(new BigDecimal("-3"), new BigDecimal("100"));
        ItemBranchBalance low = balance(new BigDecimal("5"), new BigDecimal("100"));
        low.setReorderMin(new BigDecimal("10"));
        ItemBranchBalance ok = balance(new BigDecimal("50"), new BigDecimal("100"));
        ok.setReorderMin(new BigDecimal("10"));
        when(balances.findByBranchId(BRANCH_ID)).thenReturn(java.util.List.of(neg, low, ok));

        var result = service.listBalances(BRANCH_ID, false, false);

        assertThat(result).hasSize(3);
    }

    @Test
    void listBalances_negativeOnly_filtersToNegativeRows() {
        ItemBranchBalance neg = balance(new BigDecimal("-3"), new BigDecimal("100"));
        ItemBranchBalance ok = balance(new BigDecimal("50"), new BigDecimal("100"));
        when(balances.findByBranchId(BRANCH_ID)).thenReturn(java.util.List.of(neg, ok));

        var result = service.listBalances(BRANCH_ID, true, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).qtyOnHand()).isEqualByComparingTo("-3");
    }

    @Test
    void listBalances_belowReorderOnly_filtersToAtOrBelowMin() {
        ItemBranchBalance below = balance(new BigDecimal("5"), new BigDecimal("100"));
        below.setReorderMin(new BigDecimal("10"));
        ItemBranchBalance atMin = balance(new BigDecimal("10"), new BigDecimal("100"));
        atMin.setReorderMin(new BigDecimal("10"));
        ItemBranchBalance above = balance(new BigDecimal("50"), new BigDecimal("100"));
        above.setReorderMin(new BigDecimal("10"));
        ItemBranchBalance noMin = balance(new BigDecimal("0"), new BigDecimal("100")); // reorderMin null -> filtered out
        when(balances.findByBranchId(BRANCH_ID)).thenReturn(java.util.List.of(below, atMin, above, noMin));

        var result = service.listBalances(BRANCH_ID, false, true);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(b -> b.qtyOnHand().toPlainString())
            .containsExactlyInAnyOrder("5", "10");
    }

    @Test
    void listBalances_bothFlags_composeAsAnd() {
        ItemBranchBalance neg = balance(new BigDecimal("-3"), new BigDecimal("100"));
        neg.setReorderMin(new BigDecimal("10"));    // qty<0 AND below min -> kept
        ItemBranchBalance lowNotNeg = balance(new BigDecimal("5"), new BigDecimal("100"));
        lowNotNeg.setReorderMin(new BigDecimal("10")); // below min but qty>=0 -> filtered
        ItemBranchBalance negNoMin = balance(new BigDecimal("-1"), new BigDecimal("100")); // qty<0 but reorderMin null -> filtered
        when(balances.findByBranchId(BRANCH_ID)).thenReturn(java.util.List.of(neg, lowNotNeg, negNoMin));

        var result = service.listBalances(BRANCH_ID, true, true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).qtyOnHand()).isEqualByComparingTo("-3");
    }

    @Test
    void post_passesBatchIdThroughToStockMoveRow() {
        when(balances.findByItemIdAndBranchIdForUpdate(ITEM_ID, BRANCH_ID))
            .thenReturn(Optional.of(balance(new BigDecimal("10"), new BigDecimal("150"))));

        StockMoveDto result = service.post(reqWithBatch(new BigDecimal("-3"), 4242L));

        assertThat(result.batchId()).isEqualTo(4242L);
        assertThat(result.moveType()).isEqualTo(StockMoveType.EXPIRY_WRITE_OFF);
        ArgumentCaptor<StockMove> saved = ArgumentCaptor.forClass(StockMove.class);
        verify(moves).save(saved.capture());
        assertThat(saved.getValue().getBatchId()).isEqualTo(4242L);
    }

    // ------------------------------------------------------------------
    // stockCard — docNumber + runningBalance (Slice J)
    // ------------------------------------------------------------------

    @Test
    void stockCard_resolvesDocNumberForSalesInvoice() {
        Instant at = Instant.parse("2026-04-01T08:00:00Z");
        StockMove move = makeMove(1L, at, new BigDecimal("10"), "SalesInvoice", 500L);
        when(moves.findByItemIdAndBranchIdOrderByAtAsc(eq(ITEM_ID), eq(BRANCH_ID), any()))
            .thenReturn(new PageImpl<>(List.of(move)));

        SalesInvoice inv = org.mockito.Mockito.mock(SalesInvoice.class);
        when(inv.getId()).thenReturn(500L);
        when(inv.getNumber()).thenReturn("INV-2026-0001");
        when(salesInvoices.findAllById(any())).thenReturn(List.of(inv));

        PageDto<StockMoveDto> result = service.stockCard(ITEM_ID, BRANCH_ID,
            PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).docNumber()).isEqualTo("INV-2026-0001");
    }

    @Test
    void stockCard_resolvesDocNumberForGrn() {
        StockMove move = makeMove(2L, Instant.now(), new BigDecimal("20"), "Grn", 300L);
        when(moves.findByItemIdAndBranchIdOrderByAtAsc(eq(ITEM_ID), eq(BRANCH_ID), any()))
            .thenReturn(new PageImpl<>(List.of(move)));

        Grn grn = org.mockito.Mockito.mock(Grn.class);
        when(grn.getId()).thenReturn(300L);
        when(grn.getNumber()).thenReturn("GRN-0042");
        when(grns.findAllById(any())).thenReturn(List.of(grn));

        PageDto<StockMoveDto> result = service.stockCard(ITEM_ID, BRANCH_ID,
            PageRequest.of(0, 20));

        assertThat(result.content().get(0).docNumber()).isEqualTo("GRN-0042");
    }

    @Test
    void stockCard_nullDocNumberForUnrecognisedRefType() {
        StockMove move = makeMove(3L, Instant.now(), new BigDecimal("-5"), "Adjustment", 8801L);
        when(moves.findByItemIdAndBranchIdOrderByAtAsc(eq(ITEM_ID), eq(BRANCH_ID), any()))
            .thenReturn(new PageImpl<>(List.of(move)));
        // no repo stub needed — Adjustment has no doc entity

        PageDto<StockMoveDto> result = service.stockCard(ITEM_ID, BRANCH_ID,
            PageRequest.of(0, 20));

        assertThat(result.content().get(0).docNumber()).isNull();
    }

    @Test
    void stockCard_runningBalanceAccumulatesAcrossPage() {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-02T00:00:00Z");
        Instant t3 = Instant.parse("2026-01-03T00:00:00Z");
        StockMove m1 = makeMove(10L, t1, new BigDecimal("100"), "Grn", 1L);
        StockMove m2 = makeMove(11L, t2, new BigDecimal("-30"), "SalesInvoice", 2L);
        StockMove m3 = makeMove(12L, t3, new BigDecimal("-20"), "SalesInvoice", 3L);
        when(moves.findByItemIdAndBranchIdOrderByAtAsc(eq(ITEM_ID), eq(BRANCH_ID), any()))
            .thenReturn(new PageImpl<>(List.of(m1, m2, m3)));
        // No prior moves before id=10 — opening balance is zero.
        when(moves.sumQtyBeforeId(ITEM_ID, BRANCH_ID, 10L)).thenReturn(BigDecimal.ZERO);
        // Both refTypes present on this page — both repos are queried.
        when(salesInvoices.findAllById(any())).thenReturn(List.of());
        when(grns.findAllById(any())).thenReturn(List.of());

        PageDto<StockMoveDto> result = service.stockCard(ITEM_ID, BRANCH_ID,
            PageRequest.of(0, 20));

        List<StockMoveDto> rows = result.content();
        assertThat(rows.get(0).runningBalance()).isEqualByComparingTo("100");  // +100
        assertThat(rows.get(1).runningBalance()).isEqualByComparingTo("70");   // 100 - 30
        assertThat(rows.get(2).runningBalance()).isEqualByComparingTo("50");   // 70 - 20
    }

    @Test
    void stockCard_runningBalanceCarriesOpeningBalanceFromPriorPage() {
        // Simulates page 1 (offset 10): prior 10 moves summed to 200.
        // Page 1 has one OUT move of -5; running balance must start at 200, not 0.
        Instant at = Instant.parse("2026-06-01T10:00:00Z");
        StockMove move = makeMove(20L, at, new BigDecimal("-5"), "SalesInvoice", 9L);
        when(moves.findByItemIdAndBranchIdOrderByAtAsc(eq(ITEM_ID), eq(BRANCH_ID), any()))
            .thenReturn(new PageImpl<>(List.of(move)));
        // Prior moves (id < 20) summed to 200.
        when(moves.sumQtyBeforeId(ITEM_ID, BRANCH_ID, 20L)).thenReturn(new BigDecimal("200"));
        when(salesInvoices.findAllById(any())).thenReturn(List.of());

        PageDto<StockMoveDto> result = service.stockCard(ITEM_ID, BRANCH_ID,
            PageRequest.of(1, 10));

        List<StockMoveDto> rows = result.content();
        // 200 opening + (-5) = 195
        assertThat(rows.get(0).runningBalance()).isEqualByComparingTo("195");
    }

    /** Minimal StockMove for test purposes — bypasses @PrePersist. */
    private static StockMove makeMove(Long id, Instant at, BigDecimal qty,
                                      String refType, Long refId) {
        StockMove m = new StockMove(at, ITEM_ID, BRANCH_ID, COMPANY_ID, qty,
            BigDecimal.TEN, StockMoveType.SALE, refType, refId, ACTOR_ID,
            null, null, null, null, null);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }
}

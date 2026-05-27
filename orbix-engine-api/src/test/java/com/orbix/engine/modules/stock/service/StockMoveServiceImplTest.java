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
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.repository.StockMoveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
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
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
            .thenReturn(Optional.of(balance(new BigDecimal("3"), new BigDecimal("150"))));

        assertThatThrownBy(() -> service.post(req(new BigDecimal("-5"), null, false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(Permissions.STOCK_OVERSELL);
        verify(moves, never()).save(any());
        verify(events).publish(eq("NegativeStockBlocked.v1"), any(), any(), any());
    }

    @Test
    void post_outboundBelowZero_isAllowedWithOverrideAndPermission() {
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
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
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
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
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
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

    @Test
    void post_passesBatchIdThroughToStockMoveRow() {
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
            .thenReturn(Optional.of(balance(new BigDecimal("10"), new BigDecimal("150"))));

        StockMoveDto result = service.post(reqWithBatch(new BigDecimal("-3"), 4242L));

        assertThat(result.batchId()).isEqualTo(4242L);
        assertThat(result.moveType()).isEqualTo(StockMoveType.EXPIRY_WRITE_OFF);
        ArgumentCaptor<StockMove> saved = ArgumentCaptor.forClass(StockMove.class);
        verify(moves).save(saved.capture());
        assertThat(saved.getValue().getBatchId()).isEqualTo(4242L);
    }
}

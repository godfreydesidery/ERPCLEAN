package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.admin.domain.entity.Branch;
import com.orbix.engine.modules.admin.repository.BranchRepository;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.stock.domain.dto.ItemBranchBalanceDto;
import com.orbix.engine.modules.stock.domain.dto.ItemMovementRowDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.repository.StockMoveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReportServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID  = 12L;
    private static final Long ITEM_ID_1  = 1001L;
    private static final Long ITEM_ID_2  = 1002L;

    @Mock private ItemBranchBalanceRepository balances;
    @Mock private StockMoveRepository         moves;
    @Mock private ItemRepository              items;
    @Mock private BranchRepository            branches;
    @Mock private RequestContext              context;
    @Mock private BranchScope                branchScope;

    @InjectMocks private StockReportServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        // branchScope.requireReadable(null) → null (company-wide)
        lenient().when(branchScope.requireReadable(null)).thenReturn(null);
        lenient().when(branchScope.requireReadable(BRANCH_ID)).thenReturn(BRANCH_ID);
    }

    // ------------------------------------------------------------------
    // negativeOnHand — hydration
    // ------------------------------------------------------------------

    @Test
    void negativeOnHand_hydratesItemCodeNameAndBranchName() {
        ItemBranchBalance bal = negBalance(ITEM_ID_1, BRANCH_ID, new BigDecimal("-3"));
        when(balances.findNegativeOnHand(BRANCH_ID)).thenReturn(List.of(bal));

        Item item = item(ITEM_ID_1, "COKE-500", "Coca-Cola 500ml");
        Branch branch = branch(BRANCH_ID, "Main Store");
        when(items.findAllById(any())).thenReturn(List.of(item));
        when(branches.findAllById(any())).thenReturn(List.of(branch));

        List<ItemBranchBalanceDto> result = service.negativeOnHand(BRANCH_ID);

        assertThat(result).hasSize(1);
        ItemBranchBalanceDto dto = result.get(0);
        assertThat(dto.itemCode()).isEqualTo("COKE-500");
        assertThat(dto.itemName()).isEqualTo("Coca-Cola 500ml");
        assertThat(dto.branchName()).isEqualTo("Main Store");
        assertThat(dto.qtyOnHand()).isEqualByComparingTo("-3");
    }

    @Test
    void negativeOnHand_lastMovedAt_fromBalanceEntity() {
        Instant movedAt = Instant.parse("2026-05-01T10:00:00Z");
        ItemBranchBalance bal = negBalance(ITEM_ID_1, BRANCH_ID, new BigDecimal("-1"));
        bal.setLastMovedAt(movedAt);
        when(balances.findNegativeOnHand(BRANCH_ID)).thenReturn(List.of(bal));
        when(items.findAllById(any())).thenReturn(List.of(item(ITEM_ID_1, "X", "X Item")));
        when(branches.findAllById(any())).thenReturn(List.of(branch(BRANCH_ID, "HQ")));

        List<ItemBranchBalanceDto> result = service.negativeOnHand(BRANCH_ID);

        assertThat(result.get(0).lastMovedAt()).isEqualTo(movedAt);
    }

    @Test
    void negativeOnHand_nullBranchId_returnsAllBranchesWithNames() {
        ItemBranchBalance b1 = negBalance(ITEM_ID_1, BRANCH_ID, new BigDecimal("-2"));
        ItemBranchBalance b2 = negBalance(ITEM_ID_2, 99L, new BigDecimal("-5"));
        when(balances.findNegativeOnHand(null)).thenReturn(List.of(b1, b2));

        Item item1 = item(ITEM_ID_1, "A", "Alpha");
        Item item2 = item(ITEM_ID_2, "B", "Beta");
        Branch br1 = branch(BRANCH_ID, "Branch One");
        Branch br2 = branch(99L, "Branch Two");
        when(items.findAllById(any())).thenReturn(List.of(item1, item2));
        when(branches.findAllById(any())).thenReturn(List.of(br1, br2));

        List<ItemBranchBalanceDto> result = service.negativeOnHand(null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ItemBranchBalanceDto::branchName)
            .containsExactlyInAnyOrder("Branch One", "Branch Two");
        assertThat(result).extracting(ItemBranchBalanceDto::itemCode)
            .containsExactlyInAnyOrder("A", "B");
    }

    // ------------------------------------------------------------------
    // fastMovers — moveCount + lastMoveAt
    // ------------------------------------------------------------------

    @Test
    void fastMovers_hydratesMoveCountAndLastMoveAt() {
        Instant lastAt = Instant.parse("2026-04-30T15:00:00Z");
        // row[0]=itemId, row[1]=totalQty, row[2]=count, row[3]=lastMoveAt
        List<Object[]> rows = new java.util.ArrayList<>();
        rows.add(new Object[]{ITEM_ID_1, new BigDecimal("50"), 7L, lastAt});
        when(moves.aggregateMovementByItem(eq(COMPANY_ID), eq(BRANCH_ID), any(), any(), any()))
            .thenReturn(rows);

        Item item = item(ITEM_ID_1, "SKU1", "Item One");
        when(items.findAllById(any())).thenReturn(List.of(item));
        when(balances.findById(any())).thenReturn(java.util.Optional.empty());

        List<ItemMovementRowDto> result = service.fastMovers(BRANCH_ID, null, null, null, 10);

        assertThat(result).hasSize(1);
        ItemMovementRowDto row0 = result.get(0);
        assertThat(row0.moveCount()).isEqualTo(7L);
        assertThat(row0.lastMoveAt()).isEqualTo(lastAt);
        assertThat(row0.movedQty()).isEqualByComparingTo("50");
    }

    @Test
    void slowMovers_zeroMoverItem_hasNullLastMoveAtAndZeroCount() {
        // no moves for any item in the window
        when(moves.aggregateMovementByItem(any(), any(), any(), any(), any()))
            .thenReturn(List.of());

        Item item = item(ITEM_ID_1, "ZERO", "Zero Mover");
        when(items.findByCompanyIdAndStatusOrderByIdAsc(COMPANY_ID, ItemStatus.ACTIVE))
            .thenReturn(List.of(item));
        when(balances.findByItemId(ITEM_ID_1)).thenReturn(List.of());

        List<ItemMovementRowDto> result = service.slowMovers(null, null, null, null, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).moveCount()).isEqualTo(0L);
        assertThat(result.get(0).lastMoveAt()).isNull();
        assertThat(result.get(0).movedQty()).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static ItemBranchBalance negBalance(Long itemId, Long branchId, BigDecimal qty) {
        ItemBranchBalance b = new ItemBranchBalance(itemId, branchId);
        b.setQtyOnHand(qty);
        return b;
    }

    private static Item item(Long id, String code, String name) {
        Item item = new Item(COMPANY_ID, code, name, ItemType.SELLABLE, 1L, 1L, 1L, 1L);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static Branch branch(Long id, String name) {
        Branch b = new Branch(COMPANY_ID, "BR" + id, name,
            com.orbix.engine.modules.admin.domain.enums.BranchType.GENERAL,
            "Africa/Dar_es_Salaam", false, 1L);
        ReflectionTestUtils.setField(b, "id", id);
        return b;
    }
}

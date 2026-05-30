package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.stock.domain.dto.ItemBranchBalanceDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.entity.StockMove;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.repository.StockMoveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StockReservationServiceImpl implements StockReservationService {

    private static final String AGG = "StockReservation";

    private final StockMoveRepository moves;
    private final ItemBranchBalanceRepository balances;
    private final ItemRepository items;
    private final DayGuard dayGuard;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional
    @Auditable(action = "RESERVE", entityType = AGG)
    public ItemBranchBalanceDto reserve(Long itemId, Long branchId, BigDecimal qty,
                                        String refType, Long refId, String notes) {
        requirePositive(qty);
        branchScope.requireAccess(branchId);
        requireItem(itemId);
        dayGuard.requireOpenDay(branchId);

        ItemBranchBalance balance = balances.findById(new ItemBranchBalanceId(itemId, branchId))
            .orElseGet(() -> new ItemBranchBalance(itemId, branchId));

        BigDecimal available = balance.getQtyOnHand().subtract(balance.getQtyReserved());
        if (available.compareTo(qty) < 0) {
            throw new IllegalArgumentException(
                "Insufficient availability for item " + itemId + " at branch " + branchId
                    + " — need " + qty + ", have " + available
                    + " (on-hand " + balance.getQtyOnHand()
                    + ", reserved " + balance.getQtyReserved() + ")");
        }
        balance.setQtyReserved(balance.getQtyReserved().add(qty));
        balance.setLastMovedAt(Instant.now());

        recordMove(itemId, branchId, qty, refType, refId, notes);
        balances.save(balance);
        return ItemBranchBalanceDto.from(balance);
    }

    @Override
    @Transactional
    @Auditable(action = "RELEASE", entityType = AGG)
    public ItemBranchBalanceDto release(Long itemId, Long branchId, BigDecimal qty,
                                        String refType, Long refId, String notes) {
        requirePositive(qty);
        branchScope.requireAccess(branchId);
        requireItem(itemId);
        dayGuard.requireOpenDay(branchId);

        ItemBranchBalance balance = balances.findById(new ItemBranchBalanceId(itemId, branchId))
            .orElseThrow(() -> new NoSuchElementException(
                "No balance row for item " + itemId + " at branch " + branchId + " — nothing to release"));

        BigDecimal newReserved = balance.getQtyReserved().subtract(qty);
        if (newReserved.signum() < 0) {
            throw new IllegalArgumentException(
                "Cannot release " + qty + " units — only " + balance.getQtyReserved() + " reserved");
        }
        balance.setQtyReserved(newReserved);
        balance.setLastMovedAt(Instant.now());

        recordMove(itemId, branchId, qty.negate(), refType, refId, notes);
        balances.save(balance);
        return ItemBranchBalanceDto.from(balance);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal available(Long itemId, Long branchId) {
        branchScope.requireAccess(branchId);
        return balances.findById(new ItemBranchBalanceId(itemId, branchId))
            .map(b -> b.getQtyOnHand().subtract(b.getQtyReserved()))
            .orElse(BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal qtyReserved(Long itemId, Long branchId) {
        branchScope.requireAccess(branchId);
        return balances.findById(new ItemBranchBalanceId(itemId, branchId))
            .map(ItemBranchBalance::getQtyReserved)
            .orElse(BigDecimal.ZERO);
    }

    private void recordMove(Long itemId, Long branchId, BigDecimal qty,
                            String refType, Long refId, String notes) {
        Instant now = Instant.now();
        StockMove move = moves.save(new StockMove(
            now, itemId, branchId, context.companyId(),
            qty, BigDecimal.ZERO, StockMoveType.RESERVED,
            refType, refId, context.userId(), notes,
            null, null, null, null));
        events.publish("StockReservationChanged.v1", "StockMove", String.valueOf(move.getId()),
            Map.of("stockMoveId", move.getId(),
                "itemId", itemId,
                "branchId", branchId,
                "qty", qty,
                "refType", refType,
                "refId", refId));
    }

    private static void requirePositive(BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            throw new IllegalArgumentException("qty must be positive: " + qty);
        }
    }

    private Item requireItem(Long itemId) {
        Item item = items.findById(itemId)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (!Objects.equals(item.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Item not found: " + itemId);
        }
        return item;
    }
}

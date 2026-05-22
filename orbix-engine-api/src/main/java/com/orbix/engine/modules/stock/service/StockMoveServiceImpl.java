package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.stock.domain.dto.ItemBranchBalanceDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockMoveDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.entity.StockMove;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.repository.StockMoveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StockMoveServiceImpl implements StockMoveService {

    private final StockMoveRepository moves;
    private final ItemBranchBalanceRepository balances;
    private final ItemRepository items;
    private final DayGuard dayGuard;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = "StockMove")
    public StockMoveDto post(PostStockMoveRequestDto request) {
        Long companyId = context.companyId();
        Long itemId = request.itemId();
        Long branchId = request.branchId();
        requireItem(itemId);
        dayGuard.requireOpenDay(branchId);

        Instant now = Instant.now();
        ItemBranchBalance balance = balances.findById(new ItemBranchBalanceId(itemId, branchId))
            .orElseGet(() -> new ItemBranchBalance(itemId, branchId));

        BigDecimal qty = request.qty();
        BigDecimal costAmount;
        if (qty.signum() >= 0) {
            BigDecimal unitCost = request.unitCost() != null ? request.unitCost() : BigDecimal.ZERO;
            balance.applyInbound(qty, unitCost, now);
            costAmount = unitCost;
        } else {
            BigDecimal absQty = qty.abs();
            if (balance.wouldGoNegative(absQty) && !request.allowOversell()) {
                throw new IllegalArgumentException(
                    "Insufficient stock for item " + itemId + " at branch " + branchId
                        + " — needs the STOCK.OVERSELL override");
            }
            costAmount = balance.getAvgCost();
            balance.applyOutbound(absQty, now);
        }

        StockMove move = moves.save(new StockMove(now, itemId, branchId, companyId, qty, costAmount,
            request.moveType(), request.refType(), request.refId(), context.userId(), request.notes(),
            request.batchId(), request.sectionId(), request.consumptionCategory(),
            request.authorisedByUserId()));
        balances.save(balance);

        events.publish("StockMoved.v1", "StockMove", String.valueOf(move.getId()),
            Map.of("stockMoveId", move.getId(), "itemId", itemId, "branchId", branchId,
                "qty", qty));
        events.publish("BalanceUpdated.v1", "ItemBranchBalance", itemId + ":" + branchId,
            Map.of("itemId", itemId, "branchId", branchId, "qtyOnHand", balance.getQtyOnHand()));
        if (balance.getReorderMin() != null
                && balance.getQtyOnHand().compareTo(balance.getReorderMin()) <= 0) {
            events.publish("LowStockTriggered.v1", "Item", String.valueOf(itemId),
                Map.of("itemId", itemId, "branchId", branchId, "qtyOnHand", balance.getQtyOnHand()));
        }
        return StockMoveDto.from(move);
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<StockMoveDto> listMoves(Long branchId, Pageable pageable) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        var page = scope == null
            ? moves.findByCompanyId(companyId, pageable)
            : moves.findByCompanyIdAndBranchId(companyId, scope, pageable);
        return PageDto.of(page, StockMoveDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemBranchBalanceDto> listBalances(Long branchId) {
        branchScope.requireAccess(branchId);
        return balances.findByBranchId(branchId).stream()
            .map(ItemBranchBalanceDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<StockMoveDto> stockCard(Long itemId, Long branchId, Pageable pageable) {
        branchScope.requireAccess(branchId);
        requireItem(itemId);
        return PageDto.of(
            moves.findByItemIdAndBranchIdOrderByAtAsc(itemId, branchId, pageable),
            StockMoveDto::from);
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

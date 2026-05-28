package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.domain.enums.Permissions;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.procurement.repository.GrnRepository;
import com.orbix.engine.modules.procurement.repository.VendorReturnRepository;
import com.orbix.engine.modules.sales.repository.CustomerReturnRepository;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import com.orbix.engine.modules.stock.domain.dto.ItemBranchBalanceDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockMoveDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.entity.StockMove;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.repository.StockCountRepository;
import com.orbix.engine.modules.stock.repository.StockMoveRepository;
import com.orbix.engine.modules.stock.repository.StockTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final PermissionResolverService permissions;

    // Doc-number resolvers — injected for the stock-card enrichment.
    private final SalesInvoiceRepository salesInvoices;
    private final GrnRepository grns;
    private final CustomerReturnRepository customerReturns;
    private final VendorReturnRepository vendorReturns;
    private final StockCountRepository stockCounts;
    private final StockTransferRepository stockTransfers;

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
            if (balance.wouldGoNegative(absQty)) {
                enforceOversellGate(request, itemId, branchId, absQty, balance.getQtyOnHand());
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

    /**
     * Enforces the {@code STOCK.OVERSELL} permission contract when an outbound
     * move would drive on-hand below zero.
     */
    private void enforceOversellGate(PostStockMoveRequestDto request, Long itemId, Long branchId,
                                     BigDecimal requestedQty, BigDecimal qtyOnHand) {
        if (!request.allowOversell()) {
            emitNegativeStockBlocked(itemId, branchId, requestedQty, qtyOnHand,
                request.refType(), request.refId());
            throw new IllegalArgumentException(
                "Insufficient stock for item " + itemId + " at branch " + branchId
                    + " — caller needs " + Permissions.STOCK_OVERSELL + " to override");
        }
        Long actorId = context.userId();
        boolean holdsOversell = actorId != null
            && permissions.resolve(actorId, context.companyId(), branchId)
                .contains(Permissions.STOCK_OVERSELL);
        if (!holdsOversell) {
            emitNegativeStockBlocked(itemId, branchId, requestedQty, qtyOnHand,
                request.refType(), request.refId());
            throw new IllegalArgumentException(
                "Caller does not hold " + Permissions.STOCK_OVERSELL
                    + " — cannot post a write that drives qty negative (item "
                    + itemId + ", branch " + branchId + ")");
        }
    }

    private void emitNegativeStockBlocked(Long itemId, Long branchId, BigDecimal requestedQty,
                                          BigDecimal qtyOnHand, String refType, Long refId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("itemId", itemId);
        payload.put("branchId", branchId);
        payload.put("qtyRequested", requestedQty);
        payload.put("qtyOnHand", qtyOnHand);
        payload.put("actorId", context.userId());
        payload.put("refType", refType);
        payload.put("refId", refId);
        events.publish("NegativeStockBlocked.v1", "ItemBranchBalance",
            itemId + ":" + branchId, payload);
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
    public List<ItemBranchBalanceDto> listBalances(Long branchId, boolean negativeOnly, boolean belowReorderOnly) {
        branchScope.requireAccess(branchId);
        return balances.findByBranchId(branchId).stream()
            .filter(b -> !negativeOnly || (b.getQtyOnHand() != null && b.getQtyOnHand().signum() < 0))
            .filter(b -> !belowReorderOnly
                || (b.getReorderMin() != null
                    && b.getQtyOnHand() != null
                    && b.getQtyOnHand().compareTo(b.getReorderMin()) <= 0))
            .map(ItemBranchBalanceDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<StockMoveDto> stockCard(Long itemId, Long branchId, Pageable pageable) {
        branchScope.requireAccess(branchId);
        requireItem(itemId);

        Page<StockMove> page = moves.findByItemIdAndBranchIdOrderByAtAsc(itemId, branchId, pageable);

        // Bulk-resolve doc numbers — group by refType, one query per group.
        List<StockMove> content = page.getContent();
        Map<String, String> docNumbers = resolveDocNumbers(content);

        // Accumulate running balance across the page in chronological order.
        // Already ordered by at ASC from the repository query.
        BigDecimal running = BigDecimal.ZERO;
        List<StockMoveDto> enriched = new ArrayList<>(content.size());
        for (StockMove move : content) {
            running = running.add(move.getQty());
            String docNumber = docNumbers.get(move.getRefType() + ":" + move.getRefId());
            enriched.add(new StockMoveDto(
                move.getId(),
                move.getAt(),
                move.getItemId(),
                move.getBranchId(),
                move.getCompanyId(),
                move.getQty(),
                move.getCostAmount(),
                move.getDirection(),
                move.getMoveType(),
                move.getRefType(),
                move.getRefId(),
                move.getActorId(),
                move.getNotes(),
                move.getBatchId(),
                move.getSectionId(),
                move.getConsumptionCategory(),
                move.getAuthorisedByUserId(),
                docNumber,
                running
            ));
        }

        return new PageDto<>(enriched, page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ItemBranchBalanceDto> findBalance(Long itemId, Long branchId) {
        branchScope.requireAccess(branchId);
        return balances.findById(new ItemBranchBalanceId(itemId, branchId))
            .map(ItemBranchBalanceDto::from);
    }

    private Item requireItem(Long itemId) {
        Item item = items.findById(itemId)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (!Objects.equals(item.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Item not found: " + itemId);
        }
        return item;
    }

    /**
     * Bulk-resolves document numbers for a page of stock moves. Groups moves by
     * {@code refType}, issues one {@code findAllById} per group, then maps each
     * {@code refType:refId} key to the resolved document number.
     */
    private Map<String, String> resolveDocNumbers(List<StockMove> page) {
        // Group refIds by refType.
        Map<String, Set<Long>> byType = page.stream()
            .filter(m -> m.getRefId() != null && m.getRefType() != null)
            .collect(Collectors.groupingBy(
                StockMove::getRefType,
                Collectors.mapping(StockMove::getRefId, Collectors.toSet())));

        Map<String, String> result = new HashMap<>();

        byType.forEach((refType, ids) -> {
            switch (refType) {
                case "SalesInvoice" -> salesInvoices.findAllById(ids).forEach(inv ->
                    result.put("SalesInvoice:" + inv.getId(), inv.getNumber()));
                case "Grn" -> grns.findAllById(ids).forEach(grn ->
                    result.put("Grn:" + grn.getId(), grn.getNumber()));
                case "CustomerReturn" -> customerReturns.findAllById(ids).forEach(cr ->
                    result.put("CustomerReturn:" + cr.getId(), cr.getNumber()));
                case "VendorReturn" -> vendorReturns.findAllById(ids).forEach(vr ->
                    result.put("VendorReturn:" + vr.getId(), vr.getNumber()));
                case "StockCount" -> stockCounts.findAllById(ids).forEach(sc ->
                    result.put("StockCount:" + sc.getId(), sc.getNumber()));
                case "StockTransfer" -> stockTransfers.findAllById(ids).forEach(st ->
                    result.put("StockTransfer:" + st.getId(), st.getNumber()));
                default -> { /* refType has no doc entity (e.g. Adjustment, InternalConsumption) */ }
            }
        });
        return result;
    }
}

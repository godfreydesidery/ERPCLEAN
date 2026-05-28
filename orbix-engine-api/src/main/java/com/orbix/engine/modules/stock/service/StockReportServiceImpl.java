package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.admin.domain.entity.Branch;
import com.orbix.engine.modules.admin.repository.BranchRepository;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.stock.domain.dto.ItemBranchBalanceDto;
import com.orbix.engine.modules.stock.domain.dto.ItemMovementRowDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.repository.StockMoveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockReportServiceImpl implements StockReportService {

    private static final List<StockMoveType> DEFAULT_MOVE_TYPES = List.of(StockMoveType.SALE);

    private final ItemBranchBalanceRepository balances;
    private final StockMoveRepository moves;
    private final ItemRepository items;
    private final BranchRepository branches;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional(readOnly = true)
    public List<ItemBranchBalanceDto> negativeOnHand(Long branchId) {
        Long scope = branchScope.requireReadable(branchId);
        List<ItemBranchBalance> rows = balances.findNegativeOnHand(scope);

        // Bulk-load items and branches — one query each, no N+1.
        Set<Long> itemIds = rows.stream().map(ItemBranchBalance::getItemId)
            .collect(Collectors.toSet());
        Set<Long> branchIds = rows.stream().map(ItemBranchBalance::getBranchId)
            .collect(Collectors.toSet());

        Map<Long, Item> itemMap = items.findAllById(itemIds).stream()
            .collect(Collectors.toMap(Item::getId, i -> i));
        Map<Long, Branch> branchMap = branches.findAllById(branchIds).stream()
            .collect(Collectors.toMap(Branch::getId, b -> b));

        return rows.stream()
            .map(b -> {
                Item item = itemMap.get(b.getItemId());
                Branch branch = branchMap.get(b.getBranchId());
                return ItemBranchBalanceDto.hydrated(
                    b,
                    item != null ? item.getCode() : null,
                    item != null ? item.getName() : null,
                    branch != null ? branch.getName() : null
                );
            })
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemMovementRowDto> fastMovers(Long branchId, LocalDate from, LocalDate to,
                                               List<String> moveTypes, int limit) {
        Long scope = branchScope.requireReadable(branchId);
        return rankMovers(scope, from, to, moveTypes, limit,
            Comparator.comparing(ItemMovementRowDto::movedQty).reversed());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemMovementRowDto> slowMovers(Long branchId, LocalDate from, LocalDate to,
                                               List<String> moveTypes, int limit) {
        Long scope = branchScope.requireReadable(branchId);
        // Slow movers includes zero-movement items so a long-tail item that
        // hasn't sold in the window still surfaces — query catalog for every
        // item and left-join the aggregation against it.
        MovementStats stats = movementStats(scope, from, to, moveTypes);
        Long companyId = context.companyId();

        List<Item> allActive = items.findByCompanyIdAndStatusOrderByIdAsc(companyId, ItemStatus.ACTIVE);

        return allActive.stream()
            .map(item -> toRow(item, scope,
                stats.totalQty().getOrDefault(item.getId(), BigDecimal.ZERO),
                stats.count().getOrDefault(item.getId(), 0L),
                stats.lastMoveAt().get(item.getId())))
            .sorted(Comparator.comparing(ItemMovementRowDto::movedQty))
            .limit(limit > 0 ? limit : Long.MAX_VALUE)
            .toList();
    }

    private List<ItemMovementRowDto> rankMovers(Long branchId, LocalDate from, LocalDate to,
                                                List<String> moveTypes, int limit,
                                                Comparator<ItemMovementRowDto> order) {
        MovementStats stats = movementStats(branchId, from, to, moveTypes);
        Long companyId = context.companyId();

        // Bulk-load all items at once — avoid N+1 per item.
        Set<Long> itemIds = stats.totalQty().keySet();
        Map<Long, Item> itemMap = items.findAllById(itemIds).stream()
            .collect(Collectors.toMap(Item::getId, i -> i));

        return stats.totalQty().entrySet().stream()
            .map(e -> {
                Item item = itemMap.get(e.getKey());
                if (item == null || !Objects.equals(item.getCompanyId(), companyId)) return null;
                return toRow(item, branchId, e.getValue(),
                    stats.count().getOrDefault(e.getKey(), 0L),
                    stats.lastMoveAt().get(e.getKey()));
            })
            .filter(Objects::nonNull)
            .sorted(order)
            .limit(limit > 0 ? limit : Long.MAX_VALUE)
            .toList();
    }

    private MovementStats movementStats(Long branchId, LocalDate from, LocalDate to,
                                        List<String> moveTypes) {
        Instant fromInstant = (from != null ? from : LocalDate.now().minusDays(30))
            .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = (to != null ? to.plusDays(1) : LocalDate.now().plusDays(1))
            .atStartOfDay(ZoneOffset.UTC).toInstant();
        List<StockMoveType> types = resolveMoveTypes(moveTypes);

        List<Object[]> rows = moves.aggregateMovementByItem(
            context.companyId(), branchId, types, fromInstant, toInstant);

        Map<Long, BigDecimal> totalQtyMap = new HashMap<>();
        Map<Long, Long>       countMap    = new HashMap<>();
        Map<Long, Instant>    lastMap     = new HashMap<>();

        for (Object[] row : rows) {
            Long id = ((Number) row[0]).longValue();
            totalQtyMap.put(id, (BigDecimal) row[1]);
            countMap.put(id, ((Number) row[2]).longValue());
            // row[3] is MAX(m.at) — Hibernate returns Instant for @Column Instant fields
            lastMap.put(id, row[3] instanceof Instant i ? i : null);
        }
        return new MovementStats(totalQtyMap, countMap, lastMap);
    }

    private static List<StockMoveType> resolveMoveTypes(List<String> raw) {
        if (raw == null || raw.isEmpty()) return DEFAULT_MOVE_TYPES;
        return raw.stream().map(s -> StockMoveType.valueOf(s.trim().toUpperCase())).toList();
    }

    private ItemMovementRowDto toRow(Item item, Long branchId, BigDecimal movedQty,
                                     Long moveCount, Instant lastMoveAt) {
        BigDecimal onHand = branchId != null
            ? balances.findById(new ItemBranchBalanceId(item.getId(), branchId))
                .map(ItemBranchBalance::getQtyOnHand).orElse(BigDecimal.ZERO)
            : balances.findByItemId(item.getId()).stream()
                .map(ItemBranchBalance::getQtyOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ItemMovementRowDto(item.getId(), item.getCode(), item.getName(),
            movedQty, onHand, moveCount, lastMoveAt);
    }

    /** Aggregation result holder — avoids three parallel Map pass-through parameters. */
    private record MovementStats(
        Map<Long, BigDecimal> totalQty,
        Map<Long, Long> count,
        Map<Long, Instant> lastMoveAt
    ) {}
}

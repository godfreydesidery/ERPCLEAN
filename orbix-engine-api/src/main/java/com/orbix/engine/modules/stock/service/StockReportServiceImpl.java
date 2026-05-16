package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.RequestContext;
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

@Service
@RequiredArgsConstructor
public class StockReportServiceImpl implements StockReportService {

    private static final List<StockMoveType> DEFAULT_MOVE_TYPES = List.of(StockMoveType.SALE);

    private final ItemBranchBalanceRepository balances;
    private final StockMoveRepository moves;
    private final ItemRepository items;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<ItemBranchBalanceDto> negativeOnHand(Long branchId) {
        return balances.findNegativeOnHand(branchId).stream()
            .map(ItemBranchBalanceDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemMovementRowDto> fastMovers(Long branchId, LocalDate from, LocalDate to,
                                               List<String> moveTypes, int limit) {
        return rankMovers(branchId, from, to, moveTypes, limit,
            Comparator.comparing(ItemMovementRowDto::movedQty).reversed());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemMovementRowDto> slowMovers(Long branchId, LocalDate from, LocalDate to,
                                               List<String> moveTypes, int limit) {
        // Slow movers includes zero-movement items so a long-tail item that
        // hasn't sold in the window still surfaces — query catalog for every
        // item and left-join the aggregation against it.
        Map<Long, BigDecimal> movement = movementMap(branchId, from, to, moveTypes);
        Long companyId = context.companyId();
        return items.findByCompanyIdAndStatusOrderByIdAsc(companyId, ItemStatus.ACTIVE).stream()
            .map(item -> toRow(item, branchId,
                movement.getOrDefault(item.getId(), BigDecimal.ZERO)))
            .sorted(Comparator.comparing(ItemMovementRowDto::movedQty))
            .limit(limit > 0 ? limit : Long.MAX_VALUE)
            .toList();
    }

    private List<ItemMovementRowDto> rankMovers(Long branchId, LocalDate from, LocalDate to,
                                                List<String> moveTypes, int limit,
                                                Comparator<ItemMovementRowDto> order) {
        Map<Long, BigDecimal> movement = movementMap(branchId, from, to, moveTypes);
        Long companyId = context.companyId();
        return movement.entrySet().stream()
            .map(e -> {
                Item item = items.findById(e.getKey()).orElse(null);
                if (item == null || !Objects.equals(item.getCompanyId(), companyId)) return null;
                return toRow(item, branchId, e.getValue());
            })
            .filter(Objects::nonNull)
            .sorted(order)
            .limit(limit > 0 ? limit : Long.MAX_VALUE)
            .toList();
    }

    private Map<Long, BigDecimal> movementMap(Long branchId, LocalDate from, LocalDate to,
                                              List<String> moveTypes) {
        Instant fromInstant = (from != null ? from : LocalDate.now().minusDays(30))
            .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = (to != null ? to.plusDays(1) : LocalDate.now().plusDays(1))
            .atStartOfDay(ZoneOffset.UTC).toInstant();
        List<StockMoveType> types = resolveMoveTypes(moveTypes);
        List<Object[]> rows = moves.aggregateMovementByItem(
            context.companyId(), branchId, types, fromInstant, toInstant);
        Map<Long, BigDecimal> out = new HashMap<>();
        for (Object[] row : rows) {
            out.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }
        return out;
    }

    private static List<StockMoveType> resolveMoveTypes(List<String> raw) {
        if (raw == null || raw.isEmpty()) return DEFAULT_MOVE_TYPES;
        return raw.stream().map(s -> StockMoveType.valueOf(s.trim().toUpperCase())).toList();
    }

    private ItemMovementRowDto toRow(Item item, Long branchId, BigDecimal movedQty) {
        BigDecimal onHand = branchId != null
            ? balances.findById(new ItemBranchBalanceId(item.getId(), branchId))
                .map(ItemBranchBalance::getQtyOnHand).orElse(BigDecimal.ZERO)
            : balances.findByItemId(item.getId()).stream()
                .map(ItemBranchBalance::getQtyOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ItemMovementRowDto(item.getId(), item.getCode(), item.getName(),
            movedQty, onHand);
    }
}

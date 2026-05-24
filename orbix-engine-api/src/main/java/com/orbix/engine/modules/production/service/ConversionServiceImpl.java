package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.production.domain.dto.ConversionDto;
import com.orbix.engine.modules.production.domain.dto.CreateConversionRequestDto;
import com.orbix.engine.modules.production.domain.entity.Conversion;
import com.orbix.engine.modules.production.domain.enums.ConversionStatus;
import com.orbix.engine.modules.production.repository.ConversionRepository;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.service.StockMoveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ConversionServiceImpl implements ConversionService {

    private static final int MONEY_SCALE = 4;
    private static final String AGG = "Conversion";
    private static final String F_ID = "conversionId";
    private static final String F_NUMBER = "number";

    private final ConversionRepository conversions;
    private final ItemRepository items;
    private final ItemBranchBalanceRepository balances;
    private final StockMoveService stockMoveService;
    private final DayGuard dayGuard;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = AGG)
    public ConversionDto createDraft(CreateConversionRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        branchScope.requireAccess(request.branchId());
        dayGuard.requireOpenDay(request.branchId());
        if (Objects.equals(request.fromItemId(), request.toItemId())) {
            throw new IllegalArgumentException("from_item and to_item must differ");
        }
        Item from = requireItem(request.fromItemId(), companyId);
        Item to = requireItem(request.toItemId(), companyId);

        Long fromUomId = request.fromUomId() != null ? request.fromUomId() : from.getUomId();
        Long toUomId = request.toUomId() != null ? request.toUomId() : to.getUomId();
        LocalDate date = request.conversionDate() != null ? request.conversionDate() : LocalDate.now();
        String number = resolveNumber(request.number(), request.branchId());

        Conversion conv = conversions.save(new Conversion(
            number, companyId, request.branchId(), date,
            from.getId(), request.fromQty(), fromUomId,
            to.getId(), request.toQty(), toUomId,
            request.reason(), actorId));

        events.publish("ConversionCreated.v1", AGG, String.valueOf(conv.getId()),
            Map.of(F_ID, conv.getId(), F_NUMBER, conv.getNumber(),
                "branchId", conv.getBranchId(),
                "fromItemId", conv.getFromItemId(),
                "toItemId", conv.getToItemId(),
                "fromQty", conv.getFromQty(),
                "toQty", conv.getToQty()));
        return ConversionDto.from(conv);
    }

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG)
    public ConversionDto post(String uid) {
        Conversion conv = requireConversionByUid(uid);
        if (conv.getStatus() != ConversionStatus.DRAFT) {
            throw new IllegalStateException(
                "Only DRAFT conversions can be posted (was " + conv.getStatus() + ")");
        }
        dayGuard.requireOpenDay(conv.getBranchId());

        // Derive uniform output unit_cost from from_item's avg cost so total
        // value is preserved across the split: total_cost = avg_cost × from_qty;
        // to_unit_cost = total_cost / to_qty.
        BigDecimal fromAvgCost = balances.findById(
                new ItemBranchBalanceId(conv.getFromItemId(), conv.getBranchId()))
            .map(ItemBranchBalance::getAvgCost)
            .orElse(BigDecimal.ZERO);
        BigDecimal totalCost = fromAvgCost.multiply(conv.getFromQty());
        BigDecimal toUnitCost = conv.getToQty().signum() > 0
            ? totalCost.divide(conv.getToQty(), MONEY_SCALE, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Outbound first so a negative-stock guard fires before the inbound
        // recomputes avg cost — the transaction rolls both back on failure.
        stockMoveService.post(new PostStockMoveRequestDto(
            conv.getFromItemId(), conv.getBranchId(),
            conv.getFromQty().negate(), null,
            StockMoveType.PROD_CONSUME, AGG, conv.getId(),
            "Conversion " + conv.getNumber(), false, null));
        stockMoveService.post(new PostStockMoveRequestDto(
            conv.getToItemId(), conv.getBranchId(),
            conv.getToQty(), toUnitCost,
            StockMoveType.PROD_OUTPUT, AGG, conv.getId(),
            "Conversion " + conv.getNumber(), false, null));

        conv.markPosted(toUnitCost, context.userId());
        events.publish("ConversionPosted.v1", AGG, String.valueOf(conv.getId()),
            Map.of(F_ID, conv.getId(), F_NUMBER, conv.getNumber(),
                "branchId", conv.getBranchId(),
                "fromItemId", conv.getFromItemId(),
                "fromQty", conv.getFromQty(),
                "toItemId", conv.getToItemId(),
                "toQty", conv.getToQty(),
                "unitCost", toUnitCost,
                "totalCost", totalCost));
        return ConversionDto.from(conv);
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL", entityType = AGG)
    public ConversionDto cancel(String uid) {
        Conversion conv = requireConversionByUid(uid);
        conv.cancel(context.userId());
        events.publish("ConversionCancelled.v1", AGG, String.valueOf(conv.getId()),
            Map.of(F_ID, conv.getId(), F_NUMBER, conv.getNumber()));
        return ConversionDto.from(conv);
    }

    @Override
    @Transactional(readOnly = true)
    public ConversionDto get(String uid) {
        return ConversionDto.from(requireConversionByUid(uid));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversionDto> list(Long branchId, ConversionStatus status) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        List<Conversion> rows;
        if (status != null) {
            rows = conversions.findByCompanyIdAndStatusOrderByIdDesc(companyId, status);
        } else if (scope != null) {
            rows = conversions.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, scope);
        } else {
            rows = conversions.findByCompanyIdOrderByIdDesc(companyId);
        }
        return rows.stream()
            .filter(c -> scope == null || Objects.equals(c.getBranchId(), scope))
            .map(ConversionDto::from)
            .toList();
    }

    private Conversion requireConversionByUid(String uid) {
        Conversion conv = conversions.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Conversion not found: " + uid));
        if (!Objects.equals(conv.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Conversion not found: " + uid);
        }
        branchScope.requireAccess(conv.getBranchId());
        return conv;
    }

    private Item requireItem(Long itemId, Long companyId) {
        Item item = items.findById(itemId)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (!Objects.equals(item.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Item not found: " + itemId);
        }
        return item;
    }

    private String resolveNumber(String requested, Long branchId) {
        if (requested != null && !requested.isBlank()) {
            String trimmed = requested.trim().toUpperCase();
            if (conversions.existsByBranchIdAndNumber(branchId, trimmed)) {
                throw new IllegalArgumentException(
                    "Conversion number already exists for this branch: " + trimmed);
            }
            return trimmed;
        }
        long suffix = System.currentTimeMillis() % 100_000_000L;
        String candidate = String.format("CONV-BR%d-%08d", branchId, suffix);
        if (conversions.existsByBranchIdAndNumber(branchId, candidate)) {
            candidate = candidate + "-" + (suffix % 1000);
        }
        return candidate;
    }
}

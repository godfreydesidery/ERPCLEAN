package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.SettingsService;
import com.orbix.engine.modules.iam.domain.enums.Permissions;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.stock.domain.dto.PostAdjustmentRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockMoveDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdjustmentServiceImpl implements AdjustmentService {

    static final String APPROVE_PERMISSION = Permissions.STOCK_ADJUST_APPROVE;

    private final StockMoveService stockMoveService;
    private final ItemBranchBalanceRepository balances;
    private final PermissionResolverService permissions;
    private final RequestContext context;
    private final BranchScope branchScope;
    private final SettingsService settings;
    private final EventPublisher events;

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = "StockAdjustment")
    public StockMoveDto postAdjustment(PostAdjustmentRequestDto request) {
        if (request.qty().signum() == 0) {
            throw new IllegalArgumentException("Adjustment qty must be non-zero");
        }
        branchScope.requireAccess(request.branchId());
        Long actorId = context.userId();

        // Pre-check the negative-stock invariant FIRST so a caller missing the
        // STOCK.OVERSELL override receives the right hint (the StockMoveService
        // throws the same shape but with this message, regardless of any
        // authoriser dual-control path. Without this pre-check, the dual-control
        // "authoriser required" message would win when the adjustment is also
        // above the monetary threshold).
        Optional<ItemBranchBalance> existing = balances.findById(
            new ItemBranchBalanceId(request.itemId(), request.branchId()));
        if (request.qty().signum() < 0 && !request.allowOversell()
                && existing.map(b -> b.wouldGoNegative(request.qty().abs())).orElse(true)) {
            throw new IllegalArgumentException(
                "Insufficient stock for item " + request.itemId() + " at branch "
                    + request.branchId() + " — caller needs " + Permissions.STOCK_OVERSELL
                    + " to override");
        }

        BigDecimal value = monetaryImpact(request, existing);
        boolean aboveThreshold = value.compareTo(
            settings.getDecimal(SettingKey.STOCK_ADJUSTMENT_THRESHOLD)) > 0;
        boolean needsAuthoriser = aboveThreshold || request.allowOversell();
        validateAuthoriser(request.authorisedByUserId(), actorId, needsAuthoriser);

        StockMoveDto move = stockMoveService.post(new PostStockMoveRequestDto(
            request.itemId(),
            request.branchId(),
            request.qty(),
            request.unitCost(),
            StockMoveType.ADJUSTMENT,
            "Adjustment",
            request.itemId(),  // ref_id; no separate adjustment document yet
            request.reason(),
            request.allowOversell(),
            request.batchId(),
            request.sectionId(),
            null,  // consumption_category not applicable to adjustments
            request.authorisedByUserId()
        ));

        Map<String, Object> payload = new HashMap<>();
        payload.put("stockMoveId", move.id());
        payload.put("itemId", request.itemId());
        payload.put("branchId", request.branchId());
        payload.put("qty", request.qty());
        payload.put("unitCost", request.unitCost());
        payload.put("direction", move.direction());
        payload.put("moveType", move.moveType());
        payload.put("refType", "Adjustment");
        payload.put("refId", request.itemId());
        payload.put("reason", request.reason());
        payload.put("actorId", actorId);
        payload.put("authorisedByUserId", request.authorisedByUserId());
        payload.put("aboveThreshold", aboveThreshold);
        payload.put("oversell", request.allowOversell());
        events.publish("StockAdjusted.v1", "StockMove",
            String.valueOf(move.id()), payload);

        return move;
    }

    private BigDecimal monetaryImpact(PostAdjustmentRequestDto request,
                                      Optional<ItemBranchBalance> existing) {
        BigDecimal unitValue;
        if (request.qty().signum() > 0) {
            unitValue = request.unitCost() != null ? request.unitCost() : BigDecimal.ZERO;
        } else {
            unitValue = existing.map(ItemBranchBalance::getAvgCost).orElse(BigDecimal.ZERO);
        }
        return request.qty().abs().multiply(unitValue);
    }

    private void validateAuthoriser(Long authoriserId, Long actorId, boolean required) {
        if (!required) {
            return;
        }
        if (authoriserId == null) {
            throw new IllegalArgumentException(
                "An authoriser holding " + APPROVE_PERMISSION
                    + " is required for above-threshold or oversell adjustments");
        }
        if (Objects.equals(authoriserId, actorId)) {
            throw new IllegalArgumentException("You cannot authorise your own adjustment");
        }
        boolean canApprove = permissions.resolve(authoriserId, context.companyId(), null)
            .contains(APPROVE_PERMISSION);
        if (!canApprove) {
            throw new AccessDeniedException(
                "Authoriser " + authoriserId + " does not hold " + APPROVE_PERMISSION);
        }
    }
}

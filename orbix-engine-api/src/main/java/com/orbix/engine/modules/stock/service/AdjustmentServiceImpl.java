package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdjustmentServiceImpl implements AdjustmentService {

    static final String APPROVE_PERMISSION = "STOCK.ADJUST_APPROVE";

    private final StockMoveService stockMoveService;
    private final ItemBranchBalanceRepository balances;
    private final PermissionResolverService permissions;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Value("${orbix.stock.adjustment-threshold}")
    private BigDecimal threshold;

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = "StockAdjustment")
    public StockMoveDto postAdjustment(PostAdjustmentRequestDto request) {
        if (request.qty().signum() == 0) {
            throw new IllegalArgumentException("Adjustment qty must be non-zero");
        }
        branchScope.requireAccess(request.branchId());
        Long actorId = context.userId();
        BigDecimal value = monetaryImpact(request);
        boolean aboveThreshold = value.compareTo(threshold) > 0;
        boolean needsAuthoriser = aboveThreshold || request.allowOversell();
        validateAuthoriser(request.authorisedByUserId(), actorId, needsAuthoriser);

        return stockMoveService.post(new PostStockMoveRequestDto(
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
    }

    private BigDecimal monetaryImpact(PostAdjustmentRequestDto request) {
        BigDecimal unitValue;
        if (request.qty().signum() > 0) {
            unitValue = request.unitCost() != null ? request.unitCost() : BigDecimal.ZERO;
        } else {
            unitValue = balances.findById(new ItemBranchBalanceId(request.itemId(), request.branchId()))
                .map(ItemBranchBalance::getAvgCost)
                .orElse(BigDecimal.ZERO);
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

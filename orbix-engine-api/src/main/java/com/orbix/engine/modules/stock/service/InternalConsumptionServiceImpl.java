package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.stock.domain.dto.PostInternalConsumptionRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockMoveDto;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InternalConsumptionServiceImpl implements InternalConsumptionService {

    static final String AUTHORISER_PERMISSION = "STOCK.INTERNAL_CONSUMPTION";

    private final StockMoveService stockMoveService;
    private final PermissionResolverService permissions;
    private final RequestContext context;

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = "InternalConsumption")
    public StockMoveDto postInternalConsumption(PostInternalConsumptionRequestDto request) {
        Long actorId = context.userId();
        if (Objects.equals(request.authorisedByUserId(), actorId)) {
            throw new IllegalArgumentException("You cannot authorise your own internal-consumption draw");
        }
        boolean authoriserCanApprove = permissions
            .resolve(request.authorisedByUserId(), context.companyId(), null)
            .contains(AUTHORISER_PERMISSION);
        if (!authoriserCanApprove) {
            throw new AccessDeniedException(
                "Authoriser " + request.authorisedByUserId() + " does not hold " + AUTHORISER_PERMISSION);
        }
        return stockMoveService.post(new PostStockMoveRequestDto(
            request.itemId(),
            request.branchId(),
            request.qty().negate(),
            null,
            StockMoveType.INTERNAL_CONSUMPTION,
            "InternalConsumption",
            request.itemId(),
            request.reason(),
            false,
            request.batchId(),
            request.sectionId(),
            request.consumptionCategory(),
            request.authorisedByUserId()
        ));
    }
}

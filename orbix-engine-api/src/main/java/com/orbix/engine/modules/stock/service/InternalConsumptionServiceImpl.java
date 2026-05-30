package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.stock.domain.dto.BatchPickDto;
import com.orbix.engine.modules.stock.domain.dto.PostInternalConsumptionRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockMoveDto;
import com.orbix.engine.modules.stock.domain.entity.StockBatch;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.repository.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InternalConsumptionServiceImpl implements InternalConsumptionService {

    static final String AUTHORISER_PERMISSION = "STOCK.INTERNAL_CONSUMPTION";
    private static final String REF_TYPE = "InternalConsumption";

    private final StockMoveService stockMoveService;
    private final StockBatchService stockBatchService;
    private final StockBatchRepository stockBatchRepository;
    private final ItemRepository itemRepository;
    private final PermissionResolverService permissions;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = "InternalConsumption")
    public StockMoveDto postInternalConsumption(PostInternalConsumptionRequestDto request) {
        branchScope.requireAccess(request.branchId());
        Long actorId = context.userId();
        Long companyId = context.companyId();

        if (Objects.equals(request.authorisedByUserId(), actorId)) {
            throw new IllegalArgumentException("You cannot authorise your own internal-consumption draw");
        }
        boolean authoriserCanApprove = permissions
            .resolve(request.authorisedByUserId(), companyId, null)
            .contains(AUTHORISER_PERMISSION);
        if (!authoriserCanApprove) {
            throw new AccessDeniedException(
                "Authoriser " + request.authorisedByUserId() + " does not hold " + AUTHORISER_PERMISSION);
        }

        Item item = itemRepository.findById(request.itemId())
            .filter(i -> Objects.equals(i.getCompanyId(), companyId))
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + request.itemId()));

        if (item.isBatchTracked()) {
            return postBatchTrackedConsumption(request);
        }
        return stockMoveService.post(new PostStockMoveRequestDto(
            request.itemId(),
            request.branchId(),
            request.qty().negate(),
            null,
            StockMoveType.INTERNAL_CONSUMPTION,
            REF_TYPE,
            request.itemId(),
            request.reason(),
            false,
            null,
            request.sectionId(),
            request.consumptionCategory(),
            request.authorisedByUserId()
        ));
    }

    /**
     * For batch-tracked items: drain batches FEFO (or from an explicit batch) BEFORE
     * posting the stock move, so both ledgers (batch.qty_on_hand and item_branch_balance)
     * stay consistent in the same transaction.
     * <p>
     * When {@code request.batchId()} is null → FEFO across all ACTIVE batches.
     * When {@code request.batchId()} is set  → drain exactly that batch.
     * In either case, one stock-move row is written per batch pick.
     */
    private StockMoveDto postBatchTrackedConsumption(PostInternalConsumptionRequestDto request) {
        if (request.batchId() != null) {
            // Explicit batch: load it, validate it belongs to this item/branch, drain it.
            StockBatch batch = stockBatchRepository.findById(request.batchId())
                .orElseThrow(() -> new NoSuchElementException("Stock batch not found: " + request.batchId()));
            if (!Objects.equals(batch.getItemId(), request.itemId())
                    || !Objects.equals(batch.getBranchId(), request.branchId())) {
                throw new IllegalArgumentException(
                    "Batch " + request.batchId() + " does not belong to item "
                        + request.itemId() + " at branch " + request.branchId());
            }
            batch.drain(request.qty(), context.userId());
            return stockMoveService.post(new PostStockMoveRequestDto(
                request.itemId(),
                request.branchId(),
                request.qty().negate(),
                batch.getCost(),
                StockMoveType.INTERNAL_CONSUMPTION,
                REF_TYPE,
                request.itemId(),
                request.reason(),
                false,
                batch.getId(),
                request.sectionId(),
                request.consumptionCategory(),
                request.authorisedByUserId()
            ));
        }

        // FEFO: drain earliest-expiry batches and emit one move per pick.
        List<BatchPickDto> picks = stockBatchService.drainFefo(
            request.itemId(), request.branchId(), request.qty());

        StockMoveDto lastMove = null;
        for (BatchPickDto pick : picks) {
            lastMove = stockMoveService.post(new PostStockMoveRequestDto(
                request.itemId(),
                request.branchId(),
                pick.qty().negate(),
                pick.cost(),
                StockMoveType.INTERNAL_CONSUMPTION,
                REF_TYPE,
                request.itemId(),
                request.reason(),
                false,
                pick.batchId(),
                request.sectionId(),
                request.consumptionCategory(),
                request.authorisedByUserId()
            ));
        }
        return lastMove;
    }
}

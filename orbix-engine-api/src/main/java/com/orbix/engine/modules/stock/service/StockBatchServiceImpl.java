package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.stock.domain.dto.BatchPickDto;
import com.orbix.engine.modules.stock.domain.dto.CreateStockBatchRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.RecallStockBatchRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockBatchDto;
import com.orbix.engine.modules.stock.domain.entity.StockBatch;
import com.orbix.engine.modules.stock.domain.enums.StockBatchStatus;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.repository.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StockBatchServiceImpl implements StockBatchService {

    /** Used as the {@code updatedBy} on rows touched by the scheduled expiry job. */
    static final Long SYSTEM_ACTOR_ID = 0L;

    private static final String AGG_BATCH = "StockBatch";
    private static final String F_BATCH_ID = "batchId";
    private static final String F_ITEM_ID = "itemId";
    private static final String F_BRANCH_ID = "branchId";
    private static final String F_BATCH_NO = "batchNo";

    private final StockBatchRepository batches;
    private final StockMoveService stockMoveService;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "StockBatch")
    public StockBatchDto createBatch(CreateStockBatchRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        batches.findByBranchIdAndItemIdAndBatchNo(request.branchId(), request.itemId(), request.batchNo())
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Batch already exists for this branch/item: "
                    + request.batchNo());
            });
        StockBatch batch = new StockBatch(
            request.itemId(),
            request.branchId(),
            companyId,
            request.batchNo(),
            request.manufacturedAt(),
            request.expiryAt(),
            request.qty(),
            request.cost(),
            request.sourceDocType(),
            request.sourceDocId(),
            actorId
        );
        StockBatch saved = batches.save(batch);
        events.publish("StockBatchCreated.v1", AGG_BATCH, String.valueOf(saved.getId()),
            Map.of(F_BATCH_ID, saved.getId(), F_ITEM_ID, saved.getItemId(),
                F_BRANCH_ID, saved.getBranchId(), F_BATCH_NO, saved.getBatchNo(),
                "qty", saved.getQtyReceived(), "expiryAt",
                saved.getExpiryAt() != null ? saved.getExpiryAt().toString() : ""));
        return StockBatchDto.from(saved);
    }

    @Override
    @Transactional
    public List<BatchPickDto> drainFefo(Long itemId, Long branchId, BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            throw new IllegalArgumentException("Drain qty must be positive: " + qty);
        }
        List<StockBatch> active = batches.findByItemIdAndBranchIdAndStatusOrderByExpiryAtAscIdAsc(
            itemId, branchId, StockBatchStatus.ACTIVE);
        BigDecimal remaining = qty;
        List<BatchPickDto> picks = new ArrayList<>();
        Long actorId = context.userId();
        for (StockBatch batch : active) {
            if (remaining.signum() == 0 || batch.getQtyOnHand().signum() == 0) {
                continue;
            }
            BigDecimal take = batch.getQtyOnHand().min(remaining);
            batch.drain(take, actorId);
            picks.add(new BatchPickDto(batch.getId(), batch.getBatchNo(), take, batch.getCost()));
            if (batch.getStatus() == StockBatchStatus.EXHAUSTED) {
                events.publish("StockBatchExhausted.v1", AGG_BATCH, String.valueOf(batch.getId()),
                    Map.of(F_BATCH_ID, batch.getId(), F_ITEM_ID, itemId, F_BRANCH_ID, branchId));
            }
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            throw new IllegalArgumentException(
                "Insufficient active batches for item " + itemId + " at branch " + branchId
                    + " — short " + remaining + " (FEFO needs every batch-tracked draw covered)");
        }
        return picks;
    }

    @Override
    @Transactional
    public int markExpired(LocalDate asOf) {
        if (asOf == null) {
            throw new IllegalArgumentException("asOf must not be null");
        }
        List<StockBatch> expired = batches.findByStatusAndExpiryAtBefore(StockBatchStatus.ACTIVE, asOf);
        for (StockBatch batch : expired) {
            batch.markExpired(SYSTEM_ACTOR_ID);
            events.publish("StockBatchExpired.v1", AGG_BATCH, String.valueOf(batch.getId()),
                Map.of(F_BATCH_ID, batch.getId(), F_ITEM_ID, batch.getItemId(),
                    F_BRANCH_ID, batch.getBranchId(), F_BATCH_NO, batch.getBatchNo(),
                    "qtyOnHand", batch.getQtyOnHand(),
                    "expiryAt", Objects.toString(batch.getExpiryAt(), "")));
        }
        return expired.size();
    }

    @Override
    @Transactional
    @Auditable(action = "RECALL", entityType = "StockBatch")
    public StockBatchDto recallBatch(Long batchId, RecallStockBatchRequestDto request) {
        StockBatch batch = requireBatch(batchId);
        BigDecimal remaining = batch.getQtyOnHand();
        batch.writeOffRemaining(StockBatchStatus.RECALLED, context.userId());
        if (remaining.signum() > 0) {
            stockMoveService.post(new PostStockMoveRequestDto(
                batch.getItemId(), batch.getBranchId(), remaining.negate(), null,
                StockMoveType.EXPIRY_WRITE_OFF, AGG_BATCH, batch.getId(),
                request.reason(), false, batch.getId()));
        }
        events.publish("BatchRecalled.v1", AGG_BATCH, String.valueOf(batch.getId()),
            Map.of(F_BATCH_ID, batch.getId(), F_ITEM_ID, batch.getItemId(),
                F_BRANCH_ID, batch.getBranchId(), F_BATCH_NO, batch.getBatchNo(),
                "qtyWrittenOff", remaining, "reason", request.reason()));
        return StockBatchDto.from(batch);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockBatchDto> listBatches(Long branchId, Long itemId, StockBatchStatus status) {
        Long companyId = context.companyId();
        List<StockBatch> rows;
        if (branchId != null) {
            rows = batches.findByCompanyIdAndBranchIdOrderByExpiryAtAscIdAsc(companyId, branchId);
        } else if (itemId != null) {
            rows = batches.findByCompanyIdAndItemIdOrderByExpiryAtAscIdAsc(companyId, itemId);
        } else if (status != null) {
            rows = batches.findByCompanyIdAndStatusOrderByExpiryAtAscIdAsc(companyId, status);
        } else {
            rows = batches.findByCompanyIdOrderByExpiryAtAscIdAsc(companyId);
        }
        return rows.stream()
            .filter(b -> branchId == null || Objects.equals(b.getBranchId(), branchId))
            .filter(b -> itemId == null || Objects.equals(b.getItemId(), itemId))
            .filter(b -> status == null || b.getStatus() == status)
            .map(StockBatchDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockBatchDto> listExpiringSoon(Long branchId, int daysAhead) {
        if (daysAhead < 0) {
            throw new IllegalArgumentException("daysAhead must be >= 0");
        }
        LocalDate cutoff = LocalDate.now().plusDays(daysAhead).plusDays(1);  // exclusive bound for "before"
        Long companyId = context.companyId();
        return batches.findByCompanyIdAndStatusAndExpiryAtBeforeOrderByExpiryAtAscIdAsc(
                companyId, StockBatchStatus.ACTIVE, cutoff).stream()
            .filter(b -> branchId == null || Objects.equals(b.getBranchId(), branchId))
            .filter(b -> b.getExpiryAt() != null)
            .map(StockBatchDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StockBatchDto getBatch(Long batchId) {
        return StockBatchDto.from(requireBatch(batchId));
    }

    private StockBatch requireBatch(Long batchId) {
        StockBatch batch = batches.findById(batchId)
            .orElseThrow(() -> new NoSuchElementException("Stock batch not found: " + batchId));
        if (!Objects.equals(batch.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Stock batch not found: " + batchId);
        }
        return batch;
    }
}

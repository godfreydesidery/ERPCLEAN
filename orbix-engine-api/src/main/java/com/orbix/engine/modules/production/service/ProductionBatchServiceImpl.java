package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.admin.domain.entity.Section;
import com.orbix.engine.modules.admin.repository.SectionRepository;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.production.domain.dto.AdvanceLifecycleRequestDto;
import com.orbix.engine.modules.production.domain.dto.ExplodedMaterialDto;
import com.orbix.engine.modules.production.domain.dto.PlanProductionBatchRequestDto;
import com.orbix.engine.modules.production.domain.dto.PostProductionOutputRequestDto;
import com.orbix.engine.modules.production.domain.dto.ProductionBatchDto;
import com.orbix.engine.modules.production.domain.entity.Bom;
import com.orbix.engine.modules.production.domain.entity.ProductionBatch;
import com.orbix.engine.modules.production.domain.entity.ProductionConsumption;
import com.orbix.engine.modules.production.domain.entity.ProductionOutput;
import com.orbix.engine.modules.production.domain.enums.BomStatus;
import com.orbix.engine.modules.production.domain.enums.ProductionBatchStatus;
import com.orbix.engine.modules.production.domain.enums.ProductionLifecycleState;
import com.orbix.engine.modules.production.repository.BomRepository;
import com.orbix.engine.modules.production.repository.ProductionBatchRepository;
import com.orbix.engine.modules.production.repository.ProductionConsumptionRepository;
import com.orbix.engine.modules.production.repository.ProductionOutputRepository;
import com.orbix.engine.modules.stock.domain.dto.CreateStockBatchRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.RecallStockBatchRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockBatchDto;
import com.orbix.engine.modules.stock.domain.dto.StockMoveDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.service.StockBatchService;
import com.orbix.engine.modules.stock.service.StockMoveService;
import com.orbix.engine.modules.stock.service.StockReservationService;
import lombok.RequiredArgsConstructor;
import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.service.SettingsService;
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
public class ProductionBatchServiceImpl implements ProductionBatchService {

    private static final int MONEY_SCALE = 4;
    private static final String AGG = "ProductionBatch";
    private static final String F_ID = "productionBatchId";
    private static final String F_NUMBER = "number";

    private final ProductionBatchRepository batches;
    private final ProductionConsumptionRepository consumption;
    private final ProductionOutputRepository outputs;
    private final BomRepository boms;
    private final ItemRepository items;
    private final SectionRepository sections;
    private final ItemBranchBalanceRepository balances;
    private final BomExplosionService explosion;
    private final StockReservationService reservations;
    private final StockMoveService stockMoveService;
    private final StockBatchService stockBatchService;
    private final DayGuard dayGuard;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;
    private final SettingsService settings;

    // ---------------------------------------------------------------------
    // Plan
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "PLAN", entityType = AGG)
    public ProductionBatchDto plan(PlanProductionBatchRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();

        Bom bom = requireBom(request.bomId());
        if (bom.getStatus() != BomStatus.ACTIVE) {
            throw new IllegalArgumentException(
                "BOM " + bom.getId() + " is not ACTIVE (was " + bom.getStatus() + ")");
        }
        Section section = requireSection(bom.getSectionId());
        branchScope.requireAccess(section.getBranchId());
        Long branchId = section.getBranchId();
        dayGuard.requireOpenDay(branchId);

        List<ExplodedMaterialDto> required = explosion.explode(bom.getId(), request.plannedQty());
        // Pre-flight availability check so we can return one clean error
        // instead of partially reserving and rolling back.
        for (ExplodedMaterialDto need : required) {
            BigDecimal available = reservations.available(need.inputItemId(), branchId);
            if (available.compareTo(need.qty()) < 0) {
                throw new IllegalArgumentException(
                    "Insufficient availability for material " + need.inputItemId()
                        + " at branch " + branchId + " — need " + need.qty()
                        + ", have " + available);
            }
        }

        String number = resolveNumber(request.number(), branchId);
        ProductionBatch batch = batches.save(new ProductionBatch(
            number, companyId, branchId, section.getId(),
            bom.getId(), bom.getOutputItemId(), request.plannedQty(),
            request.notes(), actorId));

        int lineNo = 1;
        for (ExplodedMaterialDto need : required) {
            reservations.reserve(need.inputItemId(), branchId, need.qty(),
                AGG, batch.getId(), "Plan " + batch.getNumber());
            consumption.save(new ProductionConsumption(
                batch.getId(), lineNo++, need.inputItemId(),
                need.qty(), need.uomId(), null));
        }

        events.publish("ProductionBatchPlanned.v1", AGG, String.valueOf(batch.getId()),
            Map.of(F_ID, batch.getId(), F_NUMBER, batch.getNumber(),
                "bomId", bom.getId(),
                "outputItemId", bom.getOutputItemId(),
                "plannedQty", batch.getPlannedQty(),
                "sectionId", batch.getSectionId(),
                "branchId", batch.getBranchId(),
                "materials", required.size()));
        return loadDto(batch);
    }

    // ---------------------------------------------------------------------
    // Start
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "START", entityType = AGG)
    public ProductionBatchDto start(Long batchId) {
        ProductionBatch batch = requireBatch(batchId);
        if (batch.getStatus() != ProductionBatchStatus.PLANNED) {
            throw new IllegalStateException(
                "Only PLANNED batches can be started (was " + batch.getStatus() + ")");
        }
        dayGuard.requireOpenDay(batch.getBranchId());
        Long actorId = context.userId();

        List<ProductionConsumption> rows = consumption.findByProductionBatchIdOrderByLineNoAsc(batch.getId());
        for (ProductionConsumption row : rows) {
            // Release the reservation first so the consume move sees the same
            // qty_on_hand it would have seen at plan time (reservation tracking
            // is independent of on-hand).
            reservations.release(row.getInputItemId(), batch.getBranchId(), row.getPlannedQty(),
                AGG, batch.getId(), "Start " + batch.getNumber());

            BigDecimal avgCost = balances.findById(
                    new ItemBranchBalanceId(row.getInputItemId(), batch.getBranchId()))
                .map(ItemBranchBalance::getAvgCost)
                .orElse(BigDecimal.ZERO);
            stockMoveService.post(new PostStockMoveRequestDto(
                row.getInputItemId(), batch.getBranchId(),
                row.getPlannedQty().negate(), null,
                StockMoveType.PROD_CONSUME, AGG, batch.getId(),
                "PROD_CONSUME " + batch.getNumber(), false, null));
            row.recordActual(row.getPlannedQty(), avgCost);
        }

        batch.markStarted(actorId);
        events.publish("ProductionBatchStarted.v1", AGG, String.valueOf(batch.getId()),
            Map.of(F_ID, batch.getId(), F_NUMBER, batch.getNumber(),
                "branchId", batch.getBranchId()));
        events.publish("ProductionConsumePosted.v1", AGG, String.valueOf(batch.getId()),
            Map.of(F_ID, batch.getId(), F_NUMBER, batch.getNumber(),
                "lines", rows.size()));
        return loadDto(batch);
    }

    // ---------------------------------------------------------------------
    // Post output
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "POST_OUTPUT", entityType = AGG)
    public ProductionBatchDto postOutput(Long batchId, PostProductionOutputRequestDto request) {
        ProductionBatch batch = requireBatch(batchId);
        // Idempotency: re-post of a COMPLETED batch is a no-op.
        if (batch.getStatus() == ProductionBatchStatus.COMPLETED) {
            return loadDto(batch);
        }
        if (batch.getStatus() != ProductionBatchStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                "Only IN_PROGRESS batches can post output (was " + batch.getStatus() + ")");
        }
        dayGuard.requireOpenDay(batch.getBranchId());

        BigDecimal totalConsumptionCost = BigDecimal.ZERO;
        for (ProductionConsumption row : consumption.findByProductionBatchIdOrderByLineNoAsc(batch.getId())) {
            BigDecimal qty = row.getActualQty() != null ? row.getActualQty() : row.getPlannedQty();
            BigDecimal unit = row.getUnitCost() != null ? row.getUnitCost() : BigDecimal.ZERO;
            totalConsumptionCost = totalConsumptionCost.add(qty.multiply(unit));
        }

        BigDecimal totalOutputQty = request.outputs().stream()
            .map(PostProductionOutputRequestDto.Line::qty)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalOutputQty.signum() <= 0) {
            throw new IllegalArgumentException("Total output qty must be positive");
        }
        validateYield(batch.getPlannedQty(), totalOutputQty);

        BigDecimal uniformUnitCost = totalOutputQty.signum() > 0
            ? totalConsumptionCost.divide(totalOutputQty, MONEY_SCALE, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        Long actorId = context.userId();
        int lineNo = 1;
        for (PostProductionOutputRequestDto.Line in : request.outputs()) {
            Item item = requireItem(in.outputItemId(), batch.getCompanyId());
            Long uomId = in.uomId() != null ? in.uomId() : item.getUomId();
            boolean primary = in.primary() != null ? in.primary() : (lineNo == 1);
            boolean packByWeight = in.packByWeight() != null && in.packByWeight();

            Long batchId2 = null;
            String batchNo = in.batchNo();
            if (item.isBatchTracked()) {
                if (batchNo == null || batchNo.isBlank()) {
                    throw new IllegalArgumentException(
                        "Output line " + lineNo + " for batch-tracked item " + item.getCode()
                            + " requires batchNo");
                }
                StockBatchDto sb = stockBatchService.createBatch(new CreateStockBatchRequestDto(
                    item.getId(), batch.getBranchId(), batchNo,
                    in.manufacturedAt() != null ? in.manufacturedAt() : LocalDate.now(),
                    in.expiryAt(),
                    in.qty(), uniformUnitCost,
                    AGG, batch.getId()));
                batchId2 = sb.id();
            }

            StockMoveDto move = stockMoveService.post(new PostStockMoveRequestDto(
                item.getId(), batch.getBranchId(),
                in.qty(), uniformUnitCost,
                StockMoveType.PROD_OUTPUT, AGG, batch.getId(),
                "PROD_OUTPUT " + batch.getNumber(), false, batchId2));

            outputs.save(new ProductionOutput(
                batch.getId(), lineNo++, item.getId(),
                in.qty(), uomId, uniformUnitCost,
                primary, packByWeight, batchId2, batchNo,
                in.manufacturedAt() != null ? in.manufacturedAt() : LocalDate.now(),
                in.expiryAt(),
                in.notes()));
            // move id captured for observability; not currently stored on the line
            Objects.requireNonNullElse(move.id(), 0L);
        }

        if (request.rejectQty() != null) {
            batch.setRejectQty(request.rejectQty());
        }
        batch.markCompleted(totalOutputQty, actorId);

        events.publish("ProductionOutputPosted.v1", AGG, String.valueOf(batch.getId()),
            Map.of(F_ID, batch.getId(), F_NUMBER, batch.getNumber(),
                "outputItemId", batch.getOutputItemId(),
                "actualQty", batch.getActualQty(),
                "plannedQty", batch.getPlannedQty(),
                "branchId", batch.getBranchId(),
                "lines", request.outputs().size(),
                "totalCost", totalConsumptionCost));
        return loadDto(batch);
    }

    // ---------------------------------------------------------------------
    // Cancel
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "CANCEL", entityType = AGG)
    public ProductionBatchDto cancel(Long batchId) {
        ProductionBatch batch = requireBatch(batchId);
        if (batch.getStatus() != ProductionBatchStatus.PLANNED) {
            throw new IllegalStateException(
                "Only PLANNED batches can be cancelled (was " + batch.getStatus() + ")");
        }
        dayGuard.requireOpenDay(batch.getBranchId());
        for (ProductionConsumption row : consumption.findByProductionBatchIdOrderByLineNoAsc(batch.getId())) {
            reservations.release(row.getInputItemId(), batch.getBranchId(), row.getPlannedQty(),
                AGG, batch.getId(), "Cancel " + batch.getNumber());
        }
        batch.cancel(context.userId());
        events.publish("ProductionBatchCancelled.v1", AGG, String.valueOf(batch.getId()),
            Map.of(F_ID, batch.getId(), F_NUMBER, batch.getNumber()));
        return loadDto(batch);
    }

    // ---------------------------------------------------------------------
    // Lifecycle (F7.3c)
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "ADVANCE_LIFECYCLE", entityType = AGG)
    public ProductionBatchDto advanceLifecycle(Long batchId, AdvanceLifecycleRequestDto request) {
        ProductionBatch batch = requireBatch(batchId);
        dayGuard.requireOpenDay(batch.getBranchId());
        Long actorId = context.userId();
        ProductionLifecycleState target = request.targetState();

        if (target == ProductionLifecycleState.OUTPUT_DONATED
                || target == ProductionLifecycleState.OUTPUT_WRITE_OFF) {
            String reason = (request.reason() == null || request.reason().isBlank())
                ? target.name() + " — " + batch.getNumber()
                : request.reason();
            writeOffRemainingOutputs(batch, reason);
            batch.markDonatedOrWriteOff(target, actorId);
        } else {
            batch.advanceLifecycle(target, actorId);
        }

        events.publish("ProductionLifecycleAdvanced.v1", AGG, String.valueOf(batch.getId()),
            Map.of(F_ID, batch.getId(), F_NUMBER, batch.getNumber(),
                "lifecycleState", batch.getLifecycleState().name(),
                "reason", request.reason() == null ? "" : request.reason()));
        return loadDto(batch);
    }

    @Override
    @Transactional
    @Auditable(action = "CLOSE", entityType = AGG)
    public ProductionBatchDto close(Long batchId) {
        ProductionBatch batch = requireBatch(batchId);
        batch.markClosed(context.userId());
        events.publish("ProductionBatchClosed.v1", AGG, String.valueOf(batch.getId()),
            Map.of(F_ID, batch.getId(), F_NUMBER, batch.getNumber(),
                "actualQty", batch.getActualQty() == null ? BigDecimal.ZERO : batch.getActualQty()));
        return loadDto(batch);
    }

    /**
     * Writes off the remaining on-hand qty of every batch-tracked
     * production_output via {@link StockBatchService#recallBatch}. Non-batch-
     * tracked outputs aren't selectively tracked by production batch in MVP —
     * their qty stays in the general balance until normal sale / consumption.
     */
    private void writeOffRemainingOutputs(ProductionBatch batch, String reason) {
        for (ProductionOutput out : outputs.findByProductionBatchIdOrderByLineNoAsc(batch.getId())) {
            if (out.getBatchId() == null) continue;
            StockBatchDto sb = stockBatchService.getBatch(out.getBatchId());
            if (sb.qtyOnHand().signum() <= 0) continue;
            stockBatchService.recallBatch(out.getBatchId(),
                new RecallStockBatchRequestDto(reason));
        }
    }

    // ---------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public ProductionBatchDto get(Long batchId) {
        return loadDto(requireBatch(batchId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductionBatchDto> list(Long branchId, Long sectionId, ProductionBatchStatus status) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        List<ProductionBatch> rows;
        if (sectionId != null) {
            rows = batches.findByCompanyIdAndSectionIdOrderByIdDesc(companyId, sectionId);
        } else if (scope != null) {
            rows = batches.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, scope);
        } else if (status != null) {
            rows = batches.findByCompanyIdAndStatusOrderByIdDesc(companyId, status);
        } else {
            rows = batches.findByCompanyIdOrderByIdDesc(companyId);
        }
        return rows.stream()
            .filter(b -> status == null || b.getStatus() == status)
            .filter(b -> scope == null || Objects.equals(b.getBranchId(), scope))
            .map(this::loadDto)
            .toList();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void validateYield(BigDecimal planned, BigDecimal actual) {
        if (planned == null || planned.signum() <= 0) return;
        BigDecimal yieldHardRejectMultiple = settings.getDecimal(SettingKey.PRODUCTION_YIELD_HARD_REJECT);
        BigDecimal hardCap = planned.multiply(yieldHardRejectMultiple);
        if (actual.compareTo(hardCap) > 0) {
            throw new IllegalArgumentException(
                "Output qty " + actual + " exceeds hard-reject cap (planned " + planned
                    + " × " + yieldHardRejectMultiple + " = " + hardCap + ")");
        }
        // Soft-warn band is enforced informationally only; the calling
        // controller surfaces it via the response notes if needed.
    }

    private String resolveNumber(String requested, Long branchId) {
        if (requested != null && !requested.isBlank()) {
            String trimmed = requested.trim().toUpperCase();
            if (batches.existsByBranchIdAndNumber(branchId, trimmed)) {
                throw new IllegalArgumentException(
                    "Batch number already exists for this branch: " + trimmed);
            }
            return trimmed;
        }
        long suffix = System.currentTimeMillis() % 100_000_000L;
        String candidate = String.format("BATCH-BR%d-%08d", branchId, suffix);
        if (batches.existsByBranchIdAndNumber(branchId, candidate)) {
            candidate = candidate + "-" + (suffix % 1000);
        }
        return candidate;
    }

    private ProductionBatch requireBatch(Long id) {
        ProductionBatch batch = batches.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Production batch not found: " + id));
        if (!Objects.equals(batch.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Production batch not found: " + id);
        }
        branchScope.requireAccess(batch.getBranchId());
        return batch;
    }

    private Bom requireBom(Long id) {
        Bom bom = boms.findById(id)
            .orElseThrow(() -> new NoSuchElementException("BOM not found: " + id));
        if (!Objects.equals(bom.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("BOM not found: " + id);
        }
        return bom;
    }

    private Item requireItem(Long itemId, Long companyId) {
        Item item = items.findById(itemId)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (!Objects.equals(item.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Item not found: " + itemId);
        }
        return item;
    }

    private Section requireSection(Long sectionId) {
        return sections.findById(sectionId)
            .orElseThrow(() -> new NoSuchElementException("Section not found: " + sectionId));
    }

    private ProductionBatchDto loadDto(ProductionBatch batch) {
        return ProductionBatchDto.from(batch,
            consumption.findByProductionBatchIdOrderByLineNoAsc(batch.getId()),
            outputs.findByProductionBatchIdOrderByLineNoAsc(batch.getId()));
    }
}

package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.production.domain.dto.ProductionVarianceRowDto;
import com.orbix.engine.modules.production.domain.entity.ProductionBatch;
import com.orbix.engine.modules.production.domain.entity.ProductionConsumption;
import com.orbix.engine.modules.production.domain.entity.ProductionWastage;
import com.orbix.engine.modules.production.domain.enums.WastageCategory;
import com.orbix.engine.modules.production.repository.ProductionBatchRepository;
import com.orbix.engine.modules.production.repository.ProductionConsumptionRepository;
import com.orbix.engine.modules.production.repository.ProductionWastageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductionVarianceReportServiceImpl implements ProductionVarianceReportService {

    private static final int PCT_SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProductionBatchRepository batches;
    private final ProductionConsumptionRepository consumption;
    private final ProductionWastageRepository wastage;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional(readOnly = true)
    public List<ProductionVarianceRowDto> report(Long branchId, Long sectionId, Long bomId,
                                                 LocalDate from, LocalDate to) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        return batches.findByCompanyIdOrderByIdDesc(companyId).stream()
            .filter(b -> scope == null || Objects.equals(b.getBranchId(), scope))
            .filter(b -> sectionId == null || Objects.equals(b.getSectionId(), sectionId))
            .filter(b -> bomId == null || Objects.equals(b.getBomId(), bomId))
            .filter(b -> within(b, from, to))
            .map(this::buildRow)
            .toList();
    }

    private ProductionVarianceRowDto buildRow(ProductionBatch batch) {
        BigDecimal totalConsumptionCost = BigDecimal.ZERO;
        for (ProductionConsumption row : consumption.findByProductionBatchIdOrderByLineNoAsc(batch.getId())) {
            BigDecimal qty = row.getActualQty() != null ? row.getActualQty() : row.getPlannedQty();
            BigDecimal unit = row.getUnitCost() != null ? row.getUnitCost() : BigDecimal.ZERO;
            totalConsumptionCost = totalConsumptionCost.add(qty.multiply(unit));
        }
        BigDecimal yieldPct = null;
        if (batch.getActualQty() != null && batch.getPlannedQty().signum() > 0) {
            yieldPct = batch.getActualQty().multiply(HUNDRED)
                .divide(batch.getPlannedQty(), PCT_SCALE, RoundingMode.HALF_UP);
        }

        Map<WastageCategory, BigDecimal> byCat = new EnumMap<>(WastageCategory.class);
        BigDecimal totalWastage = BigDecimal.ZERO;
        for (ProductionWastage w : wastage.findByProductionBatchIdOrderByRecordedAtAsc(batch.getId())) {
            byCat.merge(w.getCategory(), w.getQty(), BigDecimal::add);
            totalWastage = totalWastage.add(w.getQty());
        }

        return new ProductionVarianceRowDto(
            batch.getId(),
            batch.getNumber(),
            batch.getBranchId(),
            batch.getSectionId(),
            batch.getBomId(),
            batch.getOutputItemId(),
            batch.getPlannedQty(),
            batch.getActualQty(),
            yieldPct,
            totalConsumptionCost,
            totalWastage,
            byCat,
            batch.getStatus(),
            batch.getLifecycleState(),
            batch.getPlannedAt(),
            batch.getCompletedAt());
    }

    private static boolean within(ProductionBatch b, LocalDate from, LocalDate to) {
        LocalDate plannedDate = b.getPlannedAt().atZone(ZoneOffset.UTC).toLocalDate();
        if (from != null && plannedDate.isBefore(from)) return false;
        if (to != null && plannedDate.isAfter(to)) return false;
        return true;
    }
}

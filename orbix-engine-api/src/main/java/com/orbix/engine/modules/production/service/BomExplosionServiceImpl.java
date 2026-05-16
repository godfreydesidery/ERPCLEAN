package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.production.domain.dto.ExplodedMaterialDto;
import com.orbix.engine.modules.production.domain.entity.Bom;
import com.orbix.engine.modules.production.domain.entity.BomLine;
import com.orbix.engine.modules.production.domain.enums.BomStatus;
import com.orbix.engine.modules.production.repository.BomLineRepository;
import com.orbix.engine.modules.production.repository.BomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BomExplosionServiceImpl implements BomExplosionService {

    private static final int QTY_SCALE = 6;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BomRepository boms;
    private final BomLineRepository bomLines;

    @Override
    @Transactional(readOnly = true)
    public List<ExplodedMaterialDto> explode(Long bomId, BigDecimal plannedOutputQty) {
        if (plannedOutputQty == null || plannedOutputQty.signum() <= 0) {
            throw new IllegalArgumentException("plannedOutputQty must be positive");
        }
        Bom root = boms.findById(bomId)
            .orElseThrow(() -> new NoSuchElementException("BOM not found: " + bomId));
        // Per-(itemId, uomId) running totals; itemId-only keying assumes UoM
        // consistency across paths (validated below).
        Map<Long, ExplodedAccumulator> totals = new LinkedHashMap<>();
        Set<Long> walking = new HashSet<>();

        BigDecimal batchFactor = plannedOutputQty.divide(root.getOutputQty(),
            QTY_SCALE, RoundingMode.HALF_UP);
        walk(root, batchFactor, totals, walking);

        List<ExplodedMaterialDto> out = new ArrayList<>(totals.size());
        for (ExplodedAccumulator acc : totals.values()) {
            out.add(new ExplodedMaterialDto(acc.itemId, acc.uomId, acc.qty));
        }
        return out;
    }

    private void walk(Bom bom, BigDecimal factor, Map<Long, ExplodedAccumulator> totals,
                      Set<Long> walking) {
        if (!walking.add(bom.getId())) {
            throw new IllegalArgumentException(
                "Circular sub-BOM detected at explosion time — BOM " + bom.getId());
        }
        for (BomLine line : bomLines.findByBomIdOrderByLineNoAsc(bom.getId())) {
            BigDecimal wastageFactor = BigDecimal.ONE.add(
                line.getWastagePct().divide(HUNDRED, QTY_SCALE, RoundingMode.HALF_UP));
            BigDecimal lineQty = line.getQty().multiply(factor).multiply(wastageFactor);
            if (line.getSubBomId() != null) {
                Bom sub = boms.findById(line.getSubBomId())
                    .orElseThrow(() -> new NoSuchElementException(
                        "Sub-BOM not found: " + line.getSubBomId()));
                if (sub.getStatus() != BomStatus.ACTIVE) {
                    throw new IllegalArgumentException(
                        "Sub-BOM " + sub.getId() + " is not ACTIVE (was " + sub.getStatus() + ")");
                }
                BigDecimal subFactor = lineQty.divide(sub.getOutputQty(),
                    QTY_SCALE, RoundingMode.HALF_UP);
                walk(sub, subFactor, totals, walking);
            } else {
                Long inputItemId = line.getInputItemId();
                ExplodedAccumulator acc = totals.computeIfAbsent(inputItemId,
                    k -> new ExplodedAccumulator(inputItemId, line.getUomId()));
                if (!acc.uomId.equals(line.getUomId())) {
                    throw new IllegalArgumentException(
                        "Material " + inputItemId + " referenced with mixed UoMs across BOM paths");
                }
                acc.qty = acc.qty.add(lineQty);
            }
        }
        walking.remove(bom.getId());
    }

    private static final class ExplodedAccumulator {
        final Long itemId;
        final Long uomId;
        BigDecimal qty = BigDecimal.ZERO;

        ExplodedAccumulator(Long itemId, Long uomId) {
            this.itemId = itemId;
            this.uomId = uomId;
        }
    }
}

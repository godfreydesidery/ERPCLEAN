package com.orbix.engine.modules.production.domain.dto;

import com.orbix.engine.modules.production.domain.entity.Bom;
import com.orbix.engine.modules.production.domain.entity.BomLine;
import com.orbix.engine.modules.production.domain.enums.BomStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BomDto(
    Long id,
    String uid,
    Long companyId,
    Long sectionId,
    Long parentBomId,
    Long outputItemId,
    BigDecimal outputQty,
    Long outputUomId,
    Integer version,
    LocalDate validFrom,
    LocalDate validTo,
    BigDecimal standardYieldPct,
    BomStatus status,
    String notes,
    List<BomLineDto> lines
) {
    public static BomDto from(Bom bom, List<BomLine> lines) {
        return new BomDto(
            bom.getId(),
            bom.getUid(),
            bom.getCompanyId(),
            bom.getSectionId(),
            bom.getParentBomId(),
            bom.getOutputItemId(),
            bom.getOutputQty(),
            bom.getOutputUomId(),
            bom.getVersion(),
            bom.getValidFrom(),
            bom.getValidTo(),
            bom.getStandardYieldPct(),
            bom.getStatus(),
            bom.getNotes(),
            lines.stream().map(BomLineDto::from).toList()
        );
    }
}

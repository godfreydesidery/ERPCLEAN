package com.orbix.engine.modules.production.domain.dto;

import com.orbix.engine.modules.production.domain.entity.BomLine;

import java.math.BigDecimal;

public record BomLineDto(
    Long id,
    Integer lineNo,
    Long inputItemId,
    Long subBomId,
    BigDecimal qty,
    Long uomId,
    BigDecimal wastagePct,
    String notes
) {
    public static BomLineDto from(BomLine line) {
        return new BomLineDto(
            line.getId(),
            line.getLineNo(),
            line.getInputItemId(),
            line.getSubBomId(),
            line.getQty(),
            line.getUomId(),
            line.getWastagePct(),
            line.getNotes()
        );
    }
}

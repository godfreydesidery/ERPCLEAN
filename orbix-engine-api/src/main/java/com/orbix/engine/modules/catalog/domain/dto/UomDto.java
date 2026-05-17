package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.entity.Uom;
import com.orbix.engine.modules.catalog.domain.enums.UomDimension;

public record UomDto(
    Long id,
    String uid,
    String code,
    String name,
    UomDimension dimension,
    boolean base
) {
    public static UomDto from(Uom uom) {
        return new UomDto(uom.getId(), uom.getUid(), uom.getCode(), uom.getName(), uom.getDimension(), uom.isBase());
    }
}

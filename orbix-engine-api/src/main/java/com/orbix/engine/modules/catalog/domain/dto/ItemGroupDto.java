package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.entity.ItemGroup;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;

/** An item-group node, as returned by the catalog group endpoints. The web builds the tree from the flat list. */
public record ItemGroupDto(
    Long id,
    Long parentId,
    int level,
    String code,
    String name,
    ItemStatus status
) {
    public static ItemGroupDto from(ItemGroup group) {
        return new ItemGroupDto(
            group.getId(),
            group.getParentId(),
            group.getLevel(),
            group.getCode(),
            group.getName(),
            group.getStatus()
        );
    }
}

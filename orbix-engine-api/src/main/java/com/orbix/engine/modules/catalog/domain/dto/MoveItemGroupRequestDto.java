package com.orbix.engine.modules.catalog.domain.dto;

/** Payload for re-parenting an item group. {@code newParentId} null = move to root. */
public record MoveItemGroupRequestDto(
    Long newParentId
) {}

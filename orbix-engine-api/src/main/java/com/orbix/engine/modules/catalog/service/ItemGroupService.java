package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemGroupDto;
import com.orbix.engine.modules.catalog.domain.dto.MoveItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateItemGroupRequestDto;

import java.util.List;

/**
 * Item-group hierarchy maintenance (F1.3). The tree is self-referencing; the
 * web builds the visual tree from the flat company-scoped list. {@code level}
 * is a denormalised depth hint kept consistent on every move.
 */
public interface ItemGroupService {

    /** All groups in the caller's company, flat. */
    List<ItemGroupDto> listGroups();

    ItemGroupDto createGroup(CreateItemGroupRequestDto request);

    ItemGroupDto renameGroupByUid(String uid, UpdateItemGroupRequestDto request);

    /** Re-parents a group; the moved subtree's levels follow. Rejects cycles. */
    ItemGroupDto moveGroupByUid(String uid, MoveItemGroupRequestDto request);

    /** Soft-delete: status -> ARCHIVED. */
    void archiveGroupByUid(String uid);

    /** Reverse the soft-delete: status -> ACTIVE. */
    void activateGroupByUid(String uid);
}

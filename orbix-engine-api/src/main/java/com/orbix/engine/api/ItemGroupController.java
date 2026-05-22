package com.orbix.engine.api;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemGroupDto;
import com.orbix.engine.modules.catalog.domain.dto.MoveItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateItemGroupRequestDto;
import com.orbix.engine.modules.catalog.service.ItemGroupService;
import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Catalog item-group hierarchy (F1.3). Reuses the ITEM.* permissions. */
@RestController
@RequestMapping("/api/v1/item-groups")
@RequiredArgsConstructor
@Validated
public class ItemGroupController {

    private final ItemGroupService service;

    @GetMapping
    public List<ItemGroupDto> listGroups() {
        return service.listGroups();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ITEM.CREATE')")
    public ResponseEntity<ItemGroupDto> createGroup(
            @Valid @RequestBody CreateItemGroupRequestDto request) {
        ItemGroupDto group = service.createGroup(request);
        return ResponseEntity.created(URI.create("/api/v1/item-groups/uid/" + group.uid())).body(group);
    }

    @PatchMapping("/uid/{uid}")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public ItemGroupDto renameGroup(@PathVariable @ValidUlid String uid,
                                    @Valid @RequestBody UpdateItemGroupRequestDto request) {
        return service.renameGroupByUid(uid, request);
    }

    @PostMapping("/uid/{uid}/move")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public ItemGroupDto moveGroup(@PathVariable @ValidUlid String uid,
                                  @Valid @RequestBody MoveItemGroupRequestDto request) {
        return service.moveGroupByUid(uid, request);
    }

    @PostMapping("/uid/{uid}/archive")
    @PreAuthorize("hasAuthority('ITEM.ARCHIVE')")
    public ResponseEntity<Void> archiveGroup(@PathVariable @ValidUlid String uid) {
        service.archiveGroupByUid(uid);
        return ResponseEntity.noContent().build();
    }
}

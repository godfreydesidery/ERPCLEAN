package com.orbix.engine.api;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemGroupDto;
import com.orbix.engine.modules.catalog.domain.dto.MoveItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateItemGroupRequestDto;
import com.orbix.engine.modules.catalog.service.ItemGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Catalog item-group hierarchy (F1.3). Reuses the ITEM.* permissions. */
@RestController
@RequestMapping("/api/v1/item-groups")
@RequiredArgsConstructor
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
        return ResponseEntity.created(URI.create("/api/v1/item-groups/" + group.id())).body(group);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public ItemGroupDto renameGroup(@PathVariable Long id,
                                    @Valid @RequestBody UpdateItemGroupRequestDto request) {
        return service.renameGroup(id, request);
    }

    @PostMapping("/{id}/move")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public ItemGroupDto moveGroup(@PathVariable Long id,
                                  @Valid @RequestBody MoveItemGroupRequestDto request) {
        return service.moveGroup(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('ITEM.ARCHIVE')")
    public ResponseEntity<Void> archiveGroup(@PathVariable Long id) {
        service.archiveGroup(id);
        return ResponseEntity.noContent().build();
    }
}

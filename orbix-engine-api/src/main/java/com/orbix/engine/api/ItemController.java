package com.orbix.engine.api;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemResponseDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateItemRequestDto;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.service.ItemService;
import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Items are addressed externally by their {@code uid} (a ULID). The URL
 * shape uses a literal {@code /uid/{uid}} segment so it's never confused
 * with a code lookup or a numeric id. See {@code com.orbix.engine.modules
 * .common.domain.entity.UidEntity} for the rationale.
 */
@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
@Validated
public class ItemController {

    private final ItemService service;

    @GetMapping
    public PageDto<ItemResponseDto> listItems(
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listItems(status, PageRequest.of(page, size, Sort.by("code")));
    }

    @GetMapping("/uid/{uid}")
    public ItemResponseDto getItem(@PathVariable @ValidUlid String uid) {
        return service.getItemByUid(uid);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ITEM.CREATE')")
    public ResponseEntity<ItemResponseDto> create(@Valid @RequestBody CreateItemRequestDto request) {
        ItemResponseDto response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/items/uid/" + response.uid())).body(response);
    }

    @PatchMapping("/uid/{uid}")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public ItemResponseDto updateItem(@PathVariable @ValidUlid String uid,
                                      @Valid @RequestBody UpdateItemRequestDto request) {
        return service.updateItemByUid(uid, request);
    }

    @PostMapping("/uid/{uid}/archive")
    @PreAuthorize("hasAuthority('ITEM.ARCHIVE')")
    public ResponseEntity<Void> archiveItem(@PathVariable @ValidUlid String uid) {
        service.archiveItemByUid(uid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/uid/{uid}/activate")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public ResponseEntity<Void> activateItem(@PathVariable @ValidUlid String uid) {
        service.activateItemByUid(uid);
        return ResponseEntity.noContent().build();
    }
}

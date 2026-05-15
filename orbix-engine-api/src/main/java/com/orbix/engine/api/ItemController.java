package com.orbix.engine.api;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemResponseDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateItemRequestDto;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.service.ItemService;
import com.orbix.engine.modules.common.domain.dto.PageDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService service;

    @GetMapping
    public PageDto<ItemResponseDto> listItems(
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listItems(status, PageRequest.of(page, size, Sort.by("code")));
    }

    @GetMapping("/{id}")
    public ItemResponseDto getItem(@PathVariable Long id) {
        return service.getItem(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ITEM.CREATE')")
    public ResponseEntity<ItemResponseDto> create(@Valid @RequestBody CreateItemRequestDto request) {
        ItemResponseDto response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/items/" + response.id())).body(response);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public ItemResponseDto updateItem(@PathVariable Long id,
                                      @Valid @RequestBody UpdateItemRequestDto request) {
        return service.updateItem(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('ITEM.ARCHIVE')")
    public ResponseEntity<Void> archiveItem(@PathVariable Long id) {
        service.archiveItem(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public ResponseEntity<Void> activateItem(@PathVariable Long id) {
        service.activateItem(id);
        return ResponseEntity.noContent().build();
    }
}

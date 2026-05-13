package com.orbix.engine.api;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemRequest;
import com.orbix.engine.modules.catalog.domain.dto.ItemResponse;
import com.orbix.engine.modules.catalog.service.ItemService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/items")
public class ItemController {

    private final ItemService service;

    public ItemController(ItemService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ITEM.CREATE')")
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody CreateItemRequest request) {
        ItemResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/items/" + response.id())).body(response);
    }
}

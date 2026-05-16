package com.orbix.engine.api;

import com.orbix.engine.modules.production.domain.dto.ProductionWastageDto;
import com.orbix.engine.modules.production.domain.dto.RecordWastageRequestDto;
import com.orbix.engine.modules.production.service.ProductionWastageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Production-wastage recording (F7.3c / US-PROD-009). Append-only — wastage
 * rows are not editable after creation; correct via a compensating row.
 */
@RestController
@RequestMapping("/api/v1/production-wastage")
@RequiredArgsConstructor
public class ProductionWastageController {

    private final ProductionWastageService service;

    @PostMapping
    @PreAuthorize("hasAuthority('PROD.RECORD_WASTAGE')")
    public ResponseEntity<ProductionWastageDto> record(
            @Valid @RequestBody RecordWastageRequestDto request) {
        ProductionWastageDto saved = service.record(request);
        return ResponseEntity.created(URI.create("/api/v1/production-wastage/" + saved.id()))
            .body(saved);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROD.RECORD_WASTAGE') or hasAuthority('PROD.READ_BATCH')")
    public List<ProductionWastageDto> list(@RequestParam Long productionBatchId) {
        return service.listForBatch(productionBatchId);
    }
}

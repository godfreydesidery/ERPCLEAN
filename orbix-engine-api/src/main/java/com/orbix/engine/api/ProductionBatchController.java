package com.orbix.engine.api;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.production.domain.dto.AdvanceLifecycleRequestDto;
import com.orbix.engine.modules.production.domain.dto.PlanProductionBatchRequestDto;
import com.orbix.engine.modules.production.domain.dto.PostProductionOutputRequestDto;
import com.orbix.engine.modules.production.domain.dto.ProductionBatchDto;
import com.orbix.engine.modules.production.domain.enums.ProductionBatchStatus;
import com.orbix.engine.modules.production.service.ProductionBatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Production batch endpoints (F7.3b + F7.3c / US-PROD-003..010). Plan /
 * start / post-output / cancel / advance-lifecycle (hot → cold → discounted →
 * donated / write-off) / close. OUTPUT_DONATED + OUTPUT_WRITE_OFF run a
 * compensating write-off on every batch-tracked output's remaining on-hand.
 */
@RestController
@RequestMapping("/api/v1/production-batches")
@RequiredArgsConstructor
@Validated
public class ProductionBatchController {

    private final ProductionBatchService service;

    @PostMapping
    @PreAuthorize("hasAuthority('PROD.MANAGE_BATCH')")
    public ResponseEntity<ProductionBatchDto> plan(
            @Valid @RequestBody PlanProductionBatchRequestDto request) {
        ProductionBatchDto batch = service.plan(request);
        return ResponseEntity.created(URI.create("/api/v1/production-batches/uid/" + batch.uid()))
            .body(batch);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROD.READ_BATCH') or hasAuthority('PROD.MANAGE_BATCH')")
    public List<ProductionBatchDto> list(@RequestParam(required = false) Long branchId,
                                         @RequestParam(required = false) Long sectionId,
                                         @RequestParam(required = false) ProductionBatchStatus status) {
        return service.list(branchId, sectionId, status);
    }

    @GetMapping("/uid/{uid}")
    @PreAuthorize("hasAuthority('PROD.READ_BATCH') or hasAuthority('PROD.MANAGE_BATCH')")
    public ProductionBatchDto get(@PathVariable @ValidUlid String uid) {
        return service.get(uid);
    }

    @PostMapping("/uid/{uid}/start")
    @PreAuthorize("hasAuthority('PROD.MANAGE_BATCH')")
    public ProductionBatchDto start(@PathVariable @ValidUlid String uid) {
        return service.start(uid);
    }

    @PostMapping("/uid/{uid}/post-output")
    @PreAuthorize("hasAuthority('PROD.MANAGE_BATCH')")
    public ProductionBatchDto postOutput(@PathVariable @ValidUlid String uid,
                                         @Valid @RequestBody PostProductionOutputRequestDto request) {
        return service.postOutput(uid, request);
    }

    @PostMapping("/uid/{uid}/cancel")
    @PreAuthorize("hasAuthority('PROD.MANAGE_BATCH')")
    public ProductionBatchDto cancel(@PathVariable @ValidUlid String uid) {
        return service.cancel(uid);
    }

    @PostMapping("/uid/{uid}/advance-lifecycle")
    @PreAuthorize("hasAuthority('PROD.MANAGE_BATCH')")
    public ProductionBatchDto advanceLifecycle(@PathVariable @ValidUlid String uid,
                                               @Valid @RequestBody AdvanceLifecycleRequestDto request) {
        return service.advanceLifecycle(uid, request);
    }

    @PostMapping("/uid/{uid}/close")
    @PreAuthorize("hasAuthority('PROD.MANAGE_BATCH')")
    public ProductionBatchDto close(@PathVariable @ValidUlid String uid) {
        return service.close(uid);
    }
}

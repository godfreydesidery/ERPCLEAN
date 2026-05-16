package com.orbix.engine.api;

import com.orbix.engine.modules.production.domain.dto.ConversionDto;
import com.orbix.engine.modules.production.domain.dto.CreateConversionRequestDto;
import com.orbix.engine.modules.production.domain.enums.ConversionStatus;
import com.orbix.engine.modules.production.service.ConversionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * One-shot non-BOM item conversions (F7.4 / US-PROD-007). DRAFT -> POSTED;
 * posting writes paired PROD_CONSUME (outbound) + PROD_OUTPUT (inbound)
 * stock_moves in one transaction.
 */
@RestController
@RequestMapping("/api/v1/conversions")
@RequiredArgsConstructor
public class ConversionController {

    private final ConversionService service;

    @PostMapping
    @PreAuthorize("hasAuthority('PROD.CONVERT')")
    public ResponseEntity<ConversionDto> create(@Valid @RequestBody CreateConversionRequestDto request) {
        ConversionDto created = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/conversions/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROD.CONVERT') or hasAuthority('PROD.READ_REPORT')")
    public List<ConversionDto> list(@RequestParam(required = false) Long branchId,
                                    @RequestParam(required = false) ConversionStatus status) {
        return service.list(branchId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROD.CONVERT') or hasAuthority('PROD.READ_REPORT')")
    public ConversionDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('PROD.CONVERT')")
    public ConversionDto post(@PathVariable Long id) {
        return service.post(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PROD.CONVERT')")
    public ConversionDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}

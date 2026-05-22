package com.orbix.engine.api;

import com.orbix.engine.modules.admin.domain.dto.FxRateDto;
import com.orbix.engine.modules.admin.domain.dto.QuoteFxRateRequestDto;
import com.orbix.engine.modules.admin.service.FxRateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/** Admin FX rate management (F1.2). Gated by {@code ADMIN.MANAGE_FX}. */
@RestController
@RequestMapping("/api/v1/fx-rates")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN.MANAGE_FX')")
public class FxRateController {

    private final FxRateService service;

    @GetMapping
    public List<FxRateDto> listRates() {
        return service.listRates();
    }

    @PostMapping
    public ResponseEntity<FxRateDto> quoteRate(@Valid @RequestBody QuoteFxRateRequestDto request) {
        FxRateDto rate = service.quoteRate(request);
        return ResponseEntity.created(URI.create("/api/v1/fx-rates/" + rate.id())).body(rate);
    }

    @GetMapping("/effective")
    public ResponseEntity<FxRateDto> effectiveRate(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
        return service.effectiveRate(from, to, at)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

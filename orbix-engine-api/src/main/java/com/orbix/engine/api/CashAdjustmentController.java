package com.orbix.engine.api;

import com.orbix.engine.modules.cash.domain.dto.CashAdjustmentDto;
import com.orbix.engine.modules.cash.domain.dto.PostCashAdjustmentRequestDto;
import com.orbix.engine.modules.cash.service.CashAdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/** Supervisor cash adjustments (F6.3 / US-DAY-002). Gated by {@code CASH.ADJUST}. */
@RestController
@RequestMapping("/api/v1/cash-adjustments")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CASH.ADJUST')")
public class CashAdjustmentController {

    private final CashAdjustmentService service;

    @PostMapping
    public ResponseEntity<CashAdjustmentDto> post(@Valid @RequestBody PostCashAdjustmentRequestDto request) {
        CashAdjustmentDto saved = service.post(request);
        return ResponseEntity.created(URI.create("/api/v1/cash-adjustments/" + saved.id())).body(saved);
    }

    @GetMapping
    public List<CashAdjustmentDto> list(
            @RequestParam Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return service.list(branchId, businessDate);
    }
}

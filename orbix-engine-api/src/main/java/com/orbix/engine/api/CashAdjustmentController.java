package com.orbix.engine.api;

import com.orbix.engine.modules.cash.domain.dto.CashAdjustmentDto;
import com.orbix.engine.modules.cash.domain.dto.PostCashAdjustmentRequestDto;
import com.orbix.engine.modules.cash.service.CashAdjustmentService;
import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * Supervisor cash adjustments (F6.3 / US-DAY-002). Slice D — granular
 * per-action permissions {@code CASH.ADJUSTMENT.POST} (write) and
 * {@code CASH.ADJUSTMENT.ARCHIVE} (reverse). Read endpoints accept either
 * the granular code or the legacy coarse {@code CASH.READ} / {@code CASH.ADJUST}
 * for back-compat (group-grants).
 */
@RestController
@RequestMapping("/api/v1/cash-adjustments")
@RequiredArgsConstructor
public class CashAdjustmentController {

    private final CashAdjustmentService service;

    @PostMapping
    @PreAuthorize("hasAuthority('CASH.ADJUSTMENT.POST')")
    public ResponseEntity<CashAdjustmentDto> post(@Valid @RequestBody PostCashAdjustmentRequestDto request) {
        CashAdjustmentDto saved = service.post(request);
        return ResponseEntity
            .created(URI.create("/api/v1/cash-adjustments/uid/" + saved.uid()))
            .body(saved);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CASH.ADJUSTMENT.POST', 'CASH.ADJUST', 'CASH.READ')")
    public List<CashAdjustmentDto> list(
            @RequestParam Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return service.list(branchId, businessDate);
    }

    @GetMapping("/uid/{uid}")
    @PreAuthorize("hasAnyAuthority('CASH.ADJUSTMENT.POST', 'CASH.ADJUST', 'CASH.READ')")
    public CashAdjustmentDto getByUid(@PathVariable @ValidUlid String uid) {
        return service.getCashAdjustmentByUid(uid);
    }

    @PostMapping("/uid/{uid}/archive")
    @PreAuthorize("hasAuthority('CASH.ADJUSTMENT.ARCHIVE')")
    public CashAdjustmentDto archive(@PathVariable @ValidUlid String uid) {
        return service.archiveCashAdjustmentByUid(uid);
    }
}

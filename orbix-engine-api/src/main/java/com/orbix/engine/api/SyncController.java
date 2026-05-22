package com.orbix.engine.api;

import com.orbix.engine.modules.pos.domain.dto.BalanceSnapshotDto;
import com.orbix.engine.modules.pos.domain.dto.CatalogSnapshotDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPushRequestDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPushResultDto;
import com.orbix.engine.modules.pos.service.SyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Offline-sync endpoints for tills (F5.4). Gated by {@code POS.SYNC}. The
 * Flutter POS pulls catalog + balance snapshots when online and pushes its
 * outbox of locally-committed sales after a disconnect.
 */
@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('POS.SYNC')")
public class SyncController {

    private final SyncService service;

    @PostMapping("/push")
    public SyncPushResultDto push(@Valid @RequestBody SyncPushRequestDto request) {
        return service.pushBatch(request);
    }

    @GetMapping("/catalog/snapshot")
    public CatalogSnapshotDto catalogSnapshot(@RequestParam Long branchId,
                                              @RequestParam Long priceListId) {
        return service.catalogSnapshot(branchId, priceListId);
    }

    @GetMapping("/balances/snapshot")
    public BalanceSnapshotDto balanceSnapshot(@RequestParam Long branchId) {
        return service.balanceSnapshot(branchId);
    }
}

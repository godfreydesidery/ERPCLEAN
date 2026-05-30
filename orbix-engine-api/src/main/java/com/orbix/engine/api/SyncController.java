package com.orbix.engine.api;

import com.orbix.engine.modules.pos.domain.dto.BalanceSnapshotDto;
import com.orbix.engine.modules.pos.domain.dto.CatalogSnapshotDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPullResultDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPushRequestDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPushResultDto;
import com.orbix.engine.modules.pos.domain.dto.TillSessionCloseRequestDto;
import com.orbix.engine.modules.pos.domain.dto.TillSessionCloseResultDto;
import com.orbix.engine.modules.pos.service.SyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Offline-sync endpoints for tills and WMS (US-POS-017/018).
 * All endpoints gated by {@code POS.SYNC}; tenancy derived from JWT.
 * Design: docs/design/slice-sync-spine.md.
 *
 * <p>Contract version: {@code X-Orbix-Contract-Version} request header is
 * validated on mutating paths. Too-old client → 426; too-new → 409.
 */
@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('POS.SYNC')")
public class SyncController {

    private final SyncService service;

    @Value("${orbix.sync.contract-version:1}")
    private int serverContractVersion;

    // -----------------------------------------------------------------------
    // Push
    // -----------------------------------------------------------------------

    /**
     * POST /api/v1/sync/push — batched device-outbox upload.
     * HTTP 200 even when every op is REJECTED; read per-op verdict array.
     * Header {@code X-Orbix-Contract-Version} validated before processing.
     */
    @PostMapping("/push")
    public SyncPushResultDto push(
            @RequestHeader(value = "X-Orbix-Contract-Version", required = false) Integer headerVersion,
            @Valid @RequestBody SyncPushRequestDto request) {
        validateContractVersion(headerVersion != null ? headerVersion : request.clientContractVersion());
        return service.pushBatch(request);
    }

    // -----------------------------------------------------------------------
    // Pull / bootstrap
    // -----------------------------------------------------------------------

    /**
     * GET /api/v1/sync/pull — incremental delta since cursor.
     * {@code cursor} is the opaque token returned by the previous pull.
     * Omit or pass blank for the first pull (same as bootstrap but paged).
     * {@code datasets} is comma-separated ({@code catalog,price,balance});
     * omit for all datasets.
     */
    @GetMapping("/pull")
    public SyncPullResultDto pull(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String datasets) {
        return service.pull(cursor, datasets);
    }

    /**
     * GET /api/v1/sync/bootstrap — full snapshot + opening cursor for a
     * fresh or reinstalled device. Use once per device lifecycle.
     * {@code datasets} same semantics as {@link #pull}.
     */
    @GetMapping("/bootstrap")
    public SyncPullResultDto bootstrap(
            @RequestParam(required = false) String datasets) {
        return service.bootstrap(datasets);
    }

    // -----------------------------------------------------------------------
    // Till-session close / reconciliation
    // -----------------------------------------------------------------------

    /**
     * POST /api/v1/sync/till-session/close — reconciliation handshake.
     * Returns {@code RECONCILE_INCOMPLETE} on manifest mismatch so the client
     * can re-push missing ops and retry. Returns {@code CLOSED} on match.
     * Design §4.
     */
    @PostMapping("/till-session/close")
    public TillSessionCloseResultDto closeTillSession(
            @RequestHeader(value = "X-Orbix-Contract-Version", required = false) Integer headerVersion,
            @Valid @RequestBody TillSessionCloseRequestDto request) {
        int version = headerVersion != null ? headerVersion : serverContractVersion;
        validateContractVersion(version);
        return service.closeTillSession(request);
    }

    // -----------------------------------------------------------------------
    // Legacy snapshot endpoints (backward-compat — superseded by pull/bootstrap)
    // -----------------------------------------------------------------------

    @GetMapping("/catalog/snapshot")
    public CatalogSnapshotDto catalogSnapshot(@RequestParam Long branchId,
                                              @RequestParam Long priceListId) {
        return service.catalogSnapshot(branchId, priceListId);
    }

    @GetMapping("/balances/snapshot")
    public BalanceSnapshotDto balanceSnapshot(@RequestParam Long branchId) {
        return service.balanceSnapshot(branchId);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Validates the client's contract major against the server's.
     * <ul>
     *   <li>Client major {@literal <} server → 426 Upgrade Required (CONTRACT_TOO_OLD)</li>
     *   <li>Client major {@literal >} server → 409 Conflict (CONTRACT_TOO_NEW)</li>
     *   <li>Same major → accepted (minor differences are additive)</li>
     * </ul>
     * Design §5.4.
     */
    private void validateContractVersion(int clientMajor) {
        if (clientMajor < serverContractVersion) {
            throw new ResponseStatusException(HttpStatus.UPGRADE_REQUIRED,
                "CONTRACT_TOO_OLD: client contract major " + clientMajor
                    + " is below server minimum " + serverContractVersion
                    + " — update the app before syncing");
        }
        if (clientMajor > serverContractVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "CONTRACT_TOO_NEW: client contract major " + clientMajor
                    + " exceeds server " + serverContractVersion
                    + " — server is behind; back off and retry later");
        }
    }
}

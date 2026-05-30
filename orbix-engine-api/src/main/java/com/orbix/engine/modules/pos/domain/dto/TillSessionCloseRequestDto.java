package com.orbix.engine.modules.pos.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reconciliation handshake for POST /api/v1/sync/till-session/close.
 * The client provides a manifest of all ops it believes it sent for the session;
 * the server validates completeness before closing.
 * Design: docs/design/slice-sync-spine.md §4.
 */
public record TillSessionCloseRequestDto(
    /** clientOpId of the TILL_SESSION_OPEN op that opened this session. */
    @NotBlank String tillSessionClientOpId,
    /** Cashier's declared cash in drawer at close. */
    @NotNull @PositiveOrZero BigDecimal declaredCash,
    /** Manifest of all ops the client believes it has synced for this session. */
    @NotNull ManifestDto manifest
) {
    public record ManifestDto(
        int posSaleCount,
        @NotNull BigDecimal posSaleTotal,
        int cashPickupCount,
        @NotNull BigDecimal cashPickupTotal,
        int pettyCashCount,
        @NotNull BigDecimal pettyCashTotal,
        /** Every clientOpId the client claims is durably on the server for this session. */
        @NotEmpty List<String> clientOpIds
    ) {}
}

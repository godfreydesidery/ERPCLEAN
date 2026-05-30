package com.orbix.engine.modules.fiscal.domain.enums;

/**
 * Lifecycle status of a FiscalReceipt row, mirrored denormalized into
 * pos_sale.fiscal_status for reprint/sync-pull without crossing module boundaries.
 *
 * <ul>
 *   <li>NONE        — regime is NoOp; no fiscalization was attempted or required.</li>
 *   <li>PENDING     — outbox event received; EFDMS call not yet attempted.</li>
 *   <li>PROVISIONAL — sale posted; awaiting async EFDMS confirmation. The till
 *                     may print a provisional receipt. Transition to FISCALIZED
 *                     when EFDMS accepts.</li>
 *   <li>FISCALIZED  — EFDMS accepted the receipt; verification artefacts populated.</li>
 *   <li>FAILED      — max outbox retry attempts exhausted / dead-lettered.</li>
 *   <li>EXEMPT      — sale class does not require fiscalization (B2G, internal transfers).</li>
 * </ul>
 */
public enum FiscalStatus {
    NONE,
    PENDING,
    PROVISIONAL,
    FISCALIZED,
    FAILED,
    EXEMPT
}

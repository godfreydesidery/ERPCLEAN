package com.orbix.engine.modules.pos.domain.enums;

/**
 * POS-sale lifecycle. POS sales are committed locally on the till first and
 * pushed to the server as POSTED — there is no DRAFT. VOIDED is a same-day
 * reversal (F5.5).
 */
public enum PosSaleStatus {
    POSTED,
    VOIDED
}

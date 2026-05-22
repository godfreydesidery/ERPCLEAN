package com.orbix.engine.modules.pos.domain.enums;

/**
 * Till-session lifecycle. OPEN → CLOSED → RECONCILED. RECONCILED is a
 * supervisor-only acknowledgement that the cash count was reconciled against
 * the variance; it gates X/Z-report archiving in later slices.
 */
public enum TillSessionStatus {
    OPEN,
    CLOSED,
    RECONCILED
}

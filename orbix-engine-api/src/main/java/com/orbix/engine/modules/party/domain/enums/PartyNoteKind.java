package com.orbix.engine.modules.party.domain.enums;

/**
 * Classifies the operator workflow that produced a {@code party_note} row.
 * Stored as a VARCHAR — adding a new kind is a Java-only refactor.
 *
 * <ul>
 *   <li>{@link #AR_CHASE} — customer-AR chase activity (Slice G).</li>
 *   <li>{@link #AP_CHASE} — supplier-AP chase activity (reserved for Slice G.1).</li>
 *   <li>{@link #GENERAL} — non-debt party note (reserved).</li>
 * </ul>
 */
public enum PartyNoteKind {
    AR_CHASE,
    AP_CHASE,
    GENERAL
}

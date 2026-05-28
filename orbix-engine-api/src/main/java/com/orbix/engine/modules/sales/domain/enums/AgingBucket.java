package com.orbix.engine.modules.sales.domain.enums;

/**
 * Slice G — aging-bucket dimension on the customer-AR aging report.
 *
 * <ul>
 *   <li>{@link #CURRENT} — not yet due ({@code dueDate >= today} OR
 *       {@code dueDate is null}).</li>
 *   <li>{@link #D_1_30} — 1-30 days overdue.</li>
 *   <li>{@link #D_31_60} — 31-60 days overdue.</li>
 *   <li>{@link #D_61_90} — 61-90 days overdue.</li>
 *   <li>{@link #D_90_PLUS} — 90+ days overdue.</li>
 * </ul>
 *
 * <p>Bucket boundaries are locked per US-DEBT-003 and the Slice G plan §11.
 */
public enum AgingBucket {
    CURRENT,
    D_1_30,
    D_31_60,
    D_61_90,
    D_90_PLUS
}

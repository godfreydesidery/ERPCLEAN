package com.orbix.engine.modules.production.domain.enums;

/**
 * Reason class for a {@code production_wastage} entry. DATA-MODEL §17.11.
 *
 * <ul>
 *   <li>{@code BURNT} — overcooked / scorched (bakery, deli).</li>
 *   <li>{@code EXPIRED} — past sell-by while still in production custody.</li>
 *   <li>{@code DROPPED} — handling loss.</li>
 *   <li>{@code SAMPLED} — staff / customer tasting.</li>
 *   <li>{@code DONATED} — given away (food bank, staff meal etc.).</li>
 *   <li>{@code OTHER} — free-form, reason must explain.</li>
 * </ul>
 */
public enum WastageCategory {
    BURNT,
    EXPIRED,
    DROPPED,
    SAMPLED,
    DONATED,
    OTHER
}

package com.orbix.engine.modules.production.domain.enums;

/**
 * Production-batch coarse-grained status. DATA-MODEL §9.3.
 *
 * <ul>
 *   <li>{@code PLANNED} — materials reserved, not yet consumed.</li>
 *   <li>{@code IN_PROGRESS} — start posted; PROD_CONSUME stock moves
 *       written, output not yet captured.</li>
 *   <li>{@code COMPLETED} — output captured; further movement happens
 *       through {@link ProductionLifecycleState} (F7.3c).</li>
 *   <li>{@code CANCELLED} — terminated before start; releases reservation.</li>
 * </ul>
 */
public enum ProductionBatchStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

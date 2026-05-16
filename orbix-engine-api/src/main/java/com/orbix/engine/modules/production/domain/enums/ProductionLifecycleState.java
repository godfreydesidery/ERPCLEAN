package com.orbix.engine.modules.production.domain.enums;

/**
 * Fine-grained finished-output lifecycle. Phase 1.1 addition to
 * {@code production_batch.lifecycle_state}. F7.3b only flips
 * PLANNED -> IN_PROGRESS -> OUTPUT_HOT_DISPLAY on completion; the
 * remaining transitions land in F7.3c via the advance-lifecycle endpoint.
 *
 * <p>Transitions are forward-only except {@code OUTPUT_DONATED} /
 * {@code OUTPUT_WRITE_OFF} which are reachable from any {@code OUTPUT_*}
 * state. {@code CLOSED} terminates the batch.
 */
public enum ProductionLifecycleState {
    PLANNED,
    IN_PROGRESS,
    OUTPUT_HOT_DISPLAY,
    OUTPUT_COLD_DISPLAY,
    OUTPUT_DISCOUNTED,
    OUTPUT_DONATED,
    OUTPUT_WRITE_OFF,
    CLOSED
}

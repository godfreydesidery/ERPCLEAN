package com.orbix.engine.modules.production.domain.enums;

/**
 * BOM lifecycle. DATA-MODEL §9.1.
 *
 * <ul>
 *   <li>{@code DRAFT} — editable (lines can be added/removed/changed). Not
 *       schedulable.</li>
 *   <li>{@code ACTIVE} — immutable; production batches reference this version.
 *       Activating runs the circular-sub-BOM cycle check.</li>
 *   <li>{@code RETIRED} — superseded by a newer version OR explicitly retired.
 *       Cannot be activated again; historical batches keep their version
 *       reference for variance reporting.</li>
 * </ul>
 */
public enum BomStatus {
    DRAFT,
    ACTIVE,
    RETIRED
}

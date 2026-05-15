package com.orbix.engine.modules.procurement.domain.enums;

/** GRN lifecycle (F3.2). DRAFT → POSTED is terminal; DRAFT → CANCELLED also allowed. */
public enum GrnStatus {
    DRAFT,
    POSTED,
    CANCELLED
}

package com.orbix.engine.modules.stock.domain.enums;

/** Lifecycle of an inter-branch transfer. DRAFT -> ISSUED -> RECEIVED -> CLOSED. */
public enum StockTransferStatus { DRAFT, ISSUED, IN_TRANSIT, RECEIVED, CLOSED }

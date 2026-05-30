package com.orbix.engine.modules.fiscal.domain.enums;

/**
 * Fiscal regime codes used by the FiscalProvider SPI.
 *
 * <ul>
 *   <li>NONE    — no fiscalization; NoOpFiscalProvider handles all calls.</li>
 *   <li>TZ_VFD  — Tanzania TRA Virtual Fiscal Device; TraVfdFiscalProvider.</li>
 * </ul>
 *
 * Future regimes (KE_ETIMS, UG_EFRIS, …) are added here without touching pos.
 */
public enum FiscalRegime {
    NONE,
    TZ_VFD
}

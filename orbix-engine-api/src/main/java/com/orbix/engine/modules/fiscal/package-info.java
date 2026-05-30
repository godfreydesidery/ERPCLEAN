/**
 * Tanzania VFD fiscalization module (US-POS-011 / ADR-0006).
 *
 * <p>Owns the FiscalProvider SPI, the TraVfdFiscalProvider adapter, the
 * FiscalReceipt aggregate, and the Z-report job. Triggered exclusively via the
 * transactional outbox — the pos module emits FiscalizationRequested.v1 and this
 * module consumes it. pos does NOT depend on fiscal.
 *
 * <p>When orbix.fiscal.regime=NONE (default) the NoOpFiscalProvider is active and
 * all fiscalization calls are no-ops — non-TZ and dev deployments are unaffected.
 */
package com.orbix.engine.modules.fiscal;

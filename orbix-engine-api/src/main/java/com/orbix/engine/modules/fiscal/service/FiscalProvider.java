package com.orbix.engine.modules.fiscal.service;

import com.orbix.engine.modules.fiscal.domain.dto.FiscalizableSaleDto;
import com.orbix.engine.modules.fiscal.domain.dto.FiscalReceiptResultDto;
import com.orbix.engine.modules.fiscal.domain.dto.ZReportRequestDto;
import com.orbix.engine.modules.fiscal.domain.dto.ZReportResultDto;

/**
 * Pluggable fiscal provider SPI (ADR-0006 §3).
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link NoOpFiscalProvider} — active when {@code orbix.fiscal.regime=NONE}.
 *       Returns {@link FiscalReceiptResultDto#noOp()} without making any external call.
 *       Safe for non-TZ and dev deployments.</li>
 *   <li>{@link TraVfdFiscalProvider} — active when {@code orbix.fiscal.regime=TZ_VFD}.
 *       Builds the TRA fiscal receipt, signs it, and submits to EFDMS via
 *       {@link EfdmsClient}. The only impl of EfdmsClient right now is
 *       {@link StubEfdmsClient} — replace it when the TRA spec lands.</li>
 * </ul>
 *
 * <p>Provider selection is done by {@link FiscalProviderFactory} based on the
 * {@code orbix.fiscal.regime} config property. New regimes add a new impl of
 * this interface and a new {@link FiscalRegimeSelector} entry — no changes
 * to the pos module.
 */
public interface FiscalProvider {

    /**
     * Fiscalize one POS sale receipt.
     *
     * @param sale snapshot of the sale data needed by the fiscal device
     * @return the result including status and EFDMS artefacts; never null
     * @throws FiscalizationException if a non-recoverable error occurs
     */
    FiscalReceiptResultDto fiscalize(FiscalizableSaleDto sale);

    /**
     * Submit the end-of-day Z-report to EFDMS.
     * Advances ZNUM and resets the daily counter for the registered device.
     *
     * @param request the Z-report request
     * @return the result including new ZNUM and EFDMS acknowledgement
     */
    ZReportResultDto closeDay(ZReportRequestDto request);

    /**
     * Returns the regime code this provider handles, e.g. "TZ_VFD" or "NONE".
     * Used by {@link FiscalProviderFactory} for selection.
     */
    String regimeCode();
}

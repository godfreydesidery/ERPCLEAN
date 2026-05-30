package com.orbix.engine.modules.fiscal.service;

import com.orbix.engine.modules.fiscal.domain.dto.FiscalizableSaleDto;
import com.orbix.engine.modules.fiscal.domain.dto.FiscalReceiptResultDto;
import com.orbix.engine.modules.fiscal.domain.dto.ZReportRequestDto;
import com.orbix.engine.modules.fiscal.domain.dto.ZReportResultDto;

/**
 * THE STUB BOUNDARY (ADR-0006).
 *
 * This interface is the single class to replace when the TRA EFDMS spec lands.
 * All EFDMS network concerns live in the implementation:
 *
 * <ul>
 *   <li>Token acquisition (device credentials → bearer token)</li>
 *   <li>Receipt XML construction (line items, VAT breakdown, totals)</li>
 *   <li>RSA signing with the device .pfx key</li>
 *   <li>HTTP POST to EFDMS receipt endpoint</li>
 *   <li>Parsing the EFDMS response to extract RCTNUM, GC, DC, ZNUM,
 *       verification code, and verification URL/QR payload</li>
 *   <li>Z-report HTTP POST and response parsing</li>
 * </ul>
 *
 * Current implementation: {@link StubEfdmsClient} — returns deterministic
 * simulated values for all fields. Every simulated field is tagged
 * {@code // STUB: pending TRA EFDMS spec confirmation}.
 *
 * When the real spec is confirmed: implement {@code RealEfdmsClient} (or
 * rename {@code StubEfdmsClient}) and swap the Spring bean. No other class
 * changes.
 */
public interface EfdmsClient {

    /**
     * Submit one receipt to EFDMS and return the fiscal artefacts.
     *
     * @param sale the fiscalizable sale snapshot
     * @return a FISCALIZED result on success; implementations may throw on error
     * @throws EfdmsClientException if the EFDMS call fails (retryable or not)
     */
    FiscalReceiptResultDto submitReceipt(FiscalizableSaleDto sale);

    /**
     * Submit the end-of-day Z-report to EFDMS.
     *
     * @param request the Z-report request
     * @return the result including new ZNUM
     * @throws EfdmsClientException if the EFDMS call fails
     */
    ZReportResultDto submitZReport(ZReportRequestDto request);
}

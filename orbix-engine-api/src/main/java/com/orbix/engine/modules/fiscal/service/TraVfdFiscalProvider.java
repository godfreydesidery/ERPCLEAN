package com.orbix.engine.modules.fiscal.service;

import com.orbix.engine.modules.fiscal.domain.dto.FiscalizableSaleDto;
import com.orbix.engine.modules.fiscal.domain.dto.FiscalReceiptResultDto;
import com.orbix.engine.modules.fiscal.domain.dto.ZReportRequestDto;
import com.orbix.engine.modules.fiscal.domain.dto.ZReportResultDto;
import com.orbix.engine.modules.fiscal.domain.enums.FiscalRegime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Tanzania TRA Virtual Fiscal Device (VFD) provider — active when
 * {@code orbix.fiscal.regime=TZ_VFD}.
 *
 * <p>Implements the flow described in ADR-0006:
 * <ol>
 *   <li>Validate seller TIN is configured.</li>
 *   <li>Delegate the actual EFDMS network call to {@link EfdmsClient}.</li>
 *   <li>Return the result including fiscal artefacts.</li>
 * </ol>
 *
 * <p>The {@link EfdmsClient} is the integration boundary. Right now the only
 * implementation is {@link StubEfdmsClient}. When the TRA spec lands, swap
 * in a real EfdmsClient implementation — this class does not change.
 *
 * <p>Counter allocation (RCTNUM, GC, DC, ZNUM) is the EfdmsClient's
 * responsibility. For the real implementation these must be allocated with
 * pessimistic row-locks on a FiscalDevice entity to guarantee monotonicity
 * across concurrent submissions. The stub uses JVM atomics (sufficient for
 * single-instance dev; NOT production-safe).
 */
@Component
@ConditionalOnProperty(name = "orbix.fiscal.regime", havingValue = "TZ_VFD")
@RequiredArgsConstructor
public class TraVfdFiscalProvider implements FiscalProvider {

    private static final Logger log = LoggerFactory.getLogger(TraVfdFiscalProvider.class);

    /** THE STUB BOUNDARY — replace with a real EfdmsClient when TRA spec lands. */
    private final EfdmsClient efdmsClient;

    @Override
    public FiscalReceiptResultDto fiscalize(FiscalizableSaleDto sale) {
        log.info("TraVfdFiscalProvider: fiscalizing sale={} posSaleId={}",
            sale.saleNumber(), sale.posSaleId());

        if (sale.sellerTin() == null || sale.sellerTin().isBlank()) {
            throw new FiscalizationException(
                "orbix.fiscal.tra.tin must be configured for TZ_VFD regime");
        }

        // Delegate to EfdmsClient — the single integration boundary.
        // Exceptions from EfdmsClient propagate up and are caught by FiscalizationServiceImpl,
        // which maps them to FAILED status and lets the outbox retry.
        FiscalReceiptResultDto result = efdmsClient.submitReceipt(sale);

        log.info("TraVfdFiscalProvider: sale={} fiscalized with status={} rctnum={}",
            sale.saleNumber(), result.status(), result.rctnum());
        return result;
    }

    @Override
    public ZReportResultDto closeDay(ZReportRequestDto request) {
        log.info("TraVfdFiscalProvider: submitting Z-report for date={}", request.businessDate());
        return efdmsClient.submitZReport(request);
    }

    @Override
    public String regimeCode() {
        return FiscalRegime.TZ_VFD.name();
    }
}

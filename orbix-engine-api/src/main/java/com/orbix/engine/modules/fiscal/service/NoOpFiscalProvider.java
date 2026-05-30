package com.orbix.engine.modules.fiscal.service;

import com.orbix.engine.modules.fiscal.domain.dto.FiscalizableSaleDto;
import com.orbix.engine.modules.fiscal.domain.dto.FiscalReceiptResultDto;
import com.orbix.engine.modules.fiscal.domain.dto.ZReportRequestDto;
import com.orbix.engine.modules.fiscal.domain.dto.ZReportResultDto;
import com.orbix.engine.modules.fiscal.domain.enums.FiscalRegime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * No-op fiscal provider — active when {@code orbix.fiscal.regime=NONE}.
 *
 * <p>Returns {@link FiscalReceiptResultDto#noOp()} without making any external
 * call. Used for non-TZ deployments and local dev. Non-TZ sales are marked
 * NONE (not FAILED) — they are correctly exempted from fiscalization,
 * not a processing error.
 */
@Component
public class NoOpFiscalProvider implements FiscalProvider {

    private static final Logger log = LoggerFactory.getLogger(NoOpFiscalProvider.class);

    @Override
    public FiscalReceiptResultDto fiscalize(FiscalizableSaleDto sale) {
        log.debug("NoOpFiscalProvider: skipping fiscalization for sale {} (regime=NONE)",
            sale.saleNumber());
        return FiscalReceiptResultDto.noOp();
    }

    @Override
    public ZReportResultDto closeDay(ZReportRequestDto request) {
        log.debug("NoOpFiscalProvider: skipping Z-report for date {} (regime=NONE)",
            request.businessDate());
        return new ZReportResultDto(true, null, null, null, null, Instant.now());
    }

    @Override
    public String regimeCode() {
        return FiscalRegime.NONE.name();
    }
}

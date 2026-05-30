package com.orbix.engine.modules.fiscal.service;

import com.orbix.engine.modules.fiscal.domain.dto.FiscalizableSaleDto;
import com.orbix.engine.modules.fiscal.domain.dto.FiscalReceiptResultDto;
import com.orbix.engine.modules.fiscal.domain.dto.ZReportRequestDto;
import com.orbix.engine.modules.fiscal.domain.dto.ZReportResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * STUB EfdmsClient — the only EfdmsClient implementation until the TRA EFDMS
 * spec is confirmed. Returns deterministic simulated values for all fields.
 *
 * THIS IS THE SOLE STUB BOUNDARY. When the TRA EFDMS spec lands:
 *   1. Implement RealEfdmsClient (or rename this class) with actual HTTP calls.
 *   2. Remove @ConditionalOnProperty(matchIfMissing=true) and add the real bean.
 *   3. No other class changes — FiscalProvider, FiscalizationService, entities
 *      and DTOs are all spec-independent.
 *
 * Every simulated value in this class is tagged:
 *   // STUB: pending TRA EFDMS spec confirmation
 */
@Component
@ConditionalOnProperty(
    name = "orbix.fiscal.efdms.use-stub",
    havingValue = "true",
    matchIfMissing = true   // STUB active by default until real spec lands
)
public class StubEfdmsClient implements EfdmsClient {

    private static final Logger log = LoggerFactory.getLogger(StubEfdmsClient.class);

    // STUB: simulated monotonic counters. Real counters are per-device with
    // pessimistic row-lock allocation — not global JVM atomics.
    private static final AtomicLong RCTNUM_COUNTER = new AtomicLong(1000); // STUB: pending TRA EFDMS spec confirmation
    private static final AtomicLong GC_COUNTER     = new AtomicLong(5000); // STUB: pending TRA EFDMS spec confirmation
    private static final AtomicLong DC_COUNTER     = new AtomicLong(100);  // STUB: pending TRA EFDMS spec confirmation
    private static final int        ZNUM_STUB      = 1;                    // STUB: pending TRA EFDMS spec confirmation

    @Override
    public FiscalReceiptResultDto submitReceipt(FiscalizableSaleDto sale) {
        log.info("[STUB] Simulating EFDMS receipt submission for sale={} posSaleId={}",
            sale.saleNumber(), sale.posSaleId());

        // STUB: pending TRA EFDMS spec confirmation — real implementation will:
        //   1. Acquire a bearer token from EFDMS (POST /authenticate with device credentials)
        //   2. Build the receipt XML from sale fields (item lines, VAT breakdown, totals)
        //   3. RSA-sign the XML with the device .pfx key loaded from orbix.fiscal.tra.device-key-path
        //   4. POST the signed XML to EFDMS receipt endpoint (POST /receipts or similar)
        //   5. Parse the EFDMS response to extract RCTNUM, GC, DC, ZNUM,
        //      verification_code, verify_url, and qr_payload

        long rctnum = RCTNUM_COUNTER.getAndIncrement(); // STUB: pending TRA EFDMS spec confirmation
        long gc     = GC_COUNTER.getAndIncrement();     // STUB: pending TRA EFDMS spec confirmation
        long dc     = DC_COUNTER.getAndIncrement();     // STUB: pending TRA EFDMS spec confirmation

        String verificationCode = "STUB-VCODE-" + rctnum; // STUB: pending TRA EFDMS spec confirmation
        String verifyUrl        = "https://verify.tra.go.tz/verify?rc=" + rctnum + "&vcode=" + verificationCode; // STUB: pending TRA EFDMS spec confirmation
        String qrPayload        = verifyUrl;              // STUB: pending TRA EFDMS spec confirmation — TRA may use a different QR encoding
        String signature        = "STUB-SIG-" + sale.posSaleId() + "-" + rctnum; // STUB: pending TRA EFDMS spec confirmation
        String rawResponse      = "{\"status\":\"STUB\",\"rctnum\":" + rctnum + ",\"message\":\"Simulated EFDMS response — pending TRA spec\"}"; // STUB: pending TRA EFDMS spec confirmation

        log.info("[STUB] Simulated EFDMS response: rctnum={} gc={} dc={} verifyUrl={}",
            rctnum, gc, dc, verifyUrl);

        return FiscalReceiptResultDto.fiscalized(
            rctnum, gc, dc, ZNUM_STUB,
            verificationCode, verifyUrl, qrPayload, signature, rawResponse
        );
    }

    @Override
    public ZReportResultDto submitZReport(ZReportRequestDto request) {
        log.info("[STUB] Simulating EFDMS Z-report for businessDate={}", request.businessDate());

        // STUB: pending TRA EFDMS spec confirmation — real implementation will:
        //   1. Acquire a bearer token
        //   2. Build the Z-report XML with daily summary (total receipts, total amounts, ZNUM)
        //   3. RSA-sign and POST to EFDMS Z-report endpoint
        //   4. Parse response to confirm new ZNUM

        int newZnum = ZNUM_STUB + 1; // STUB: pending TRA EFDMS spec confirmation
        String ackRef = "STUB-ZREPORT-" + request.businessDate() + "-" + newZnum; // STUB: pending TRA EFDMS spec confirmation

        log.info("[STUB] Simulated Z-report ack: newZnum={} ackRef={}", newZnum, ackRef);

        return new ZReportResultDto(true, newZnum, ackRef,
            "{\"status\":\"STUB\",\"znum\":" + newZnum + "}", // STUB: pending TRA EFDMS spec confirmation
            null, Instant.now());
    }
}

package com.orbix.engine.modules.stock.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Scheduled FEFO expiry-flag job (F2.4). Flips ACTIVE batches whose {@code expiry_at}
 * is before today to EXPIRED and emits {@code StockBatchExpired.v1} per row. The
 * actual write-off of remaining on-hand is a separate operator action (recall
 * endpoint) — this job only flags.
 */
@Component
@RequiredArgsConstructor
public class StockBatchExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(StockBatchExpiryJob.class);

    private final StockBatchService batches;

    @Scheduled(cron = "${orbix.stock.batch-expiry-cron}")
    public void flagExpiredBatches() {
        int flipped = batches.markExpired(LocalDate.now());
        if (flipped > 0) {
            log.info("StockBatch expiry job flagged {} batch(es) as EXPIRED", flipped);
        }
    }
}

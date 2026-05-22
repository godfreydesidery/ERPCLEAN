package com.orbix.engine.modules.orders.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled customer-order expiry job (F7.2 / US-ORD-006). Finds open orders
 * past {@code reserved_until}, releases the reservation (LAYBY), forfeits the
 * paid deposit per company policy, flips status to {@code EXPIRED}.
 *
 * <p>Runs daily at {@code orbix.orders.expiry-cron} (default 00:15 UTC, ten
 * minutes after the gift-card expiry sweep). Idempotent across runs because
 * EXPIRED orders no longer match the predicate.
 */
@Component
@RequiredArgsConstructor
public class CustomerOrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(CustomerOrderExpiryJob.class);

    private final CustomerOrderService orders;

    @Scheduled(cron = "${orbix.orders.expiry-cron}")
    public void expireDueOrders() {
        int expired = orders.runExpiryJob();
        if (expired > 0) {
            log.info("Customer order expiry job retired {} order(s)", expired);
        }
    }
}

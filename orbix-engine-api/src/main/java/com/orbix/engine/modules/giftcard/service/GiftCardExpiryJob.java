package com.orbix.engine.modules.giftcard.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled gift-card expiry job (F7.1 / US-GC-007). Flips ACTIVE cards
 * past {@code expires_at} to EXPIRED and writes an EXPIRE txn for the
 * remaining balance (breakage). Idempotent across runs because already-
 * EXPIRED cards no longer match the predicate.
 */
@Component
@RequiredArgsConstructor
public class GiftCardExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(GiftCardExpiryJob.class);

    private final GiftCardService giftCards;

    @Scheduled(cron = "${orbix.giftcard.expiry-cron}")
    public void expireDueCards() {
        int expired = giftCards.runExpiryJob();
        if (expired > 0) {
            log.info("Gift card expiry job retired {} card(s)", expired);
        }
    }
}

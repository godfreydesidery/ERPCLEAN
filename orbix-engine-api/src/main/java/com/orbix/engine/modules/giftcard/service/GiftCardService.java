package com.orbix.engine.modules.giftcard.service;

import com.orbix.engine.modules.giftcard.domain.dto.GiftCardDto;
import com.orbix.engine.modules.giftcard.domain.dto.GiftCardTxnDto;
import com.orbix.engine.modules.giftcard.domain.dto.IssueGiftCardRequestDto;
import com.orbix.engine.modules.giftcard.domain.dto.RedeemGiftCardRequestDto;
import com.orbix.engine.modules.giftcard.domain.dto.RefundGiftCardRequestDto;

import java.util.List;

/**
 * Gift-card lifecycle (F7.1). Owns the balance ledger; the cash side of
 * issuance is delegated to {@link com.orbix.engine.modules.cash.service.CashLedgerService}.
 * Redemption and refund-credit are pure liability transfers — they do NOT
 * touch {@code cash_entry}.
 *
 * <p>{@link #redeem} and {@link #refundCredit} are idempotent on the
 * {@code (refDocType, refDocId, kind)} key.
 */
public interface GiftCardService {

    GiftCardDto issue(IssueGiftCardRequestDto request);

    GiftCardDto lookup(String code);

    List<GiftCardTxnDto> listTransactions(String code);

    GiftCardTxnDto redeem(String code, RedeemGiftCardRequestDto request);

    GiftCardTxnDto refundCredit(String code, RefundGiftCardRequestDto request);

    GiftCardDto freeze(String code);

    GiftCardDto unfreeze(String code);

    /**
     * Scheduled-job hook. Finds ACTIVE / FULLY_REDEEMED cards past
     * {@code expires_at} and posts an {@code EXPIRE} txn for the remaining
     * balance. Idempotent: an already-EXPIRED card is a no-op.
     *
     * @return number of cards expired
     */
    int runExpiryJob();
}

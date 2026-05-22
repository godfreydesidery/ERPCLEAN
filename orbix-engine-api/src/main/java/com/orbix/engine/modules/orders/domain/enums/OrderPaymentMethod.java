package com.orbix.engine.modules.orders.domain.enums;

/**
 * Deposit / instalment payment methods accepted on a {@code customer_order_payment}.
 * DATA-MODEL §17.10.
 *
 * <p>Mirrors {@link com.orbix.engine.modules.cash.domain.enums.PaymentMethod}
 * plus {@code GIFT_CARD} (settled via {@link
 * com.orbix.engine.modules.giftcard.service.GiftCardService#redeem} rather
 * than a cash entry — gift-card redemption is a liability transfer).
 */
public enum OrderPaymentMethod {
    CASH,
    CARD,
    BANK_TRANSFER,
    MOBILE_MONEY,
    CHEQUE,
    GIFT_CARD
}

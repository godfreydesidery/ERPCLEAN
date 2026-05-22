package com.orbix.engine.modules.giftcard.domain.enums;

/** Lifecycle of a gift card. DATA-MODEL §17.6. */
public enum GiftCardStatus {
    /** Loaded and redeemable. */
    ACTIVE,
    /** Balance hit zero through redemption. */
    FULLY_REDEEMED,
    /** Past {@code expires_at}; balance written off as breakage. */
    EXPIRED,
    /** Manager-locked (lost / stolen). Cannot redeem or refund-credit until unfrozen. */
    FROZEN,
    /** Refunded — full balance returned, card retired. */
    REFUNDED
}

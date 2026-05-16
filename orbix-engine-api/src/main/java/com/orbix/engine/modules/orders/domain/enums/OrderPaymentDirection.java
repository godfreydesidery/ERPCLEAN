package com.orbix.engine.modules.orders.domain.enums;

/**
 * Direction of a {@code customer_order_payment} row — {@code IN} for a
 * customer-initiated deposit / instalment / final payment, {@code OUT} for
 * a refund on cancellation per the configured policy.
 */
public enum OrderPaymentDirection {
    IN,
    OUT
}

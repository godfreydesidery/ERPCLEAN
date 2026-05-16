package com.orbix.engine.modules.orders.domain.enums;

/**
 * Customer-order lifecycle. DATA-MODEL §17.8.
 *
 * <p>Forward-only:
 * {@code DRAFT -> RESERVED -> DEPOSIT_PAID -> PARTIALLY_PAID -> READY -> COLLECTED}.
 * Branches: {@code CANCELLED} from any pre-{@code COLLECTED} state;
 * {@code EXPIRED} from {@code RESERVED} / {@code DEPOSIT_PAID} /
 * {@code PARTIALLY_PAID} by the scheduled job.
 *
 * <p>For {@code PRE_ORDER} the {@code RESERVED} state is skipped (no stock
 * to lock — production produces the goods); the order moves
 * {@code DRAFT -> DEPOSIT_PAID} directly when the deposit is captured.
 */
public enum CustomerOrderStatus {
    DRAFT,
    RESERVED,
    DEPOSIT_PAID,
    PARTIALLY_PAID,
    READY,
    COLLECTED,
    CANCELLED,
    EXPIRED;

    public boolean isOpen() {
        return this != COLLECTED && this != CANCELLED && this != EXPIRED;
    }

    public boolean isTerminal() {
        return !isOpen();
    }
}

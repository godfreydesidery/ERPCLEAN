package com.orbix.engine.modules.orders.service;

import com.orbix.engine.modules.orders.domain.dto.CancelCustomerOrderRequestDto;
import com.orbix.engine.modules.orders.domain.dto.CreateCustomerOrderRequestDto;
import com.orbix.engine.modules.orders.domain.dto.CustomerOrderDto;
import com.orbix.engine.modules.orders.domain.dto.PatchCustomerOrderRequestDto;
import com.orbix.engine.modules.orders.domain.dto.PayCustomerOrderRequestDto;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderStatus;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderType;

import java.util.List;

/**
 * Layby + pre-order lifecycle (F7.2). Owns the customer-order aggregate;
 * defers cash-side bookkeeping to {@code CashLedgerService}, stock
 * reservation to {@code StockReservationService}, gift-card redemption to
 * {@code GiftCardService}.
 */
public interface CustomerOrderService {

    CustomerOrderDto create(CreateCustomerOrderRequestDto request);

    CustomerOrderDto patch(Long orderId, PatchCustomerOrderRequestDto request);

    /**
     * Lock stock against the order (LAYBY only — PRE_ORDER is produce-to-order
     * and skips reservation). Transitions DRAFT -> RESERVED.
     */
    CustomerOrderDto reserve(Long orderId);

    /**
     * Record a deposit / instalment / final payment. Advances the status per
     * {@link com.orbix.engine.modules.orders.domain.entity.CustomerOrder#applyPayment}
     * rules. Idempotent on {@code (orderId, idempotencyKey)}.
     */
    CustomerOrderDto pay(Long orderId, PayCustomerOrderRequestDto request);

    /**
     * Cancel an open order. Releases any reservation, refunds prior payments
     * per the configured cancel-window policy, sets status CANCELLED.
     */
    CustomerOrderDto cancel(Long orderId, CancelCustomerOrderRequestDto request);

    /**
     * Manual READY transition for PRE_ORDER — replaces the (deferred)
     * {@code ProductionOutputPosted.v1} consumer once production lands in F7.3.
     */
    CustomerOrderDto markReady(Long orderId);

    /**
     * Final collection — converts LAYBY reservation to a SALE stock_move (or
     * for PRE_ORDER simply posts the SALE move against the produced goods),
     * sets status COLLECTED. Requires balance_due = 0.
     */
    CustomerOrderDto collect(Long orderId);

    CustomerOrderDto get(Long orderId);

    List<CustomerOrderDto> list(Long branchId, Long customerId, CustomerOrderStatus status,
                                CustomerOrderType type);

    /**
     * Scheduled-job hook. Finds open orders past {@code reserved_until},
     * releases reservation, forfeits deposit, flips status to EXPIRED.
     *
     * @return number of orders expired
     */
    int runExpiryJob();
}

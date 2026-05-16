package com.orbix.engine.modules.orders.domain.enums;

/**
 * Flavour of a {@code customer_order}. DATA-MODEL §17.8.
 *
 * <ul>
 *   <li>{@code LAYBY} — instalment purchase. On reserve, stock is locked
 *       via {@link com.orbix.engine.modules.stock.service.StockReservationService};
 *       collection converts the reservation to a {@code SALE} stock_move.</li>
 *   <li>{@code PRE_ORDER} — production-tied. No stock reservation (production
 *       creates the goods). Deposit triggers a production batch via
 *       {@code ProductionRequested.v1}; the order moves to READY when production
 *       posts output.</li>
 * </ul>
 */
public enum CustomerOrderType {
    LAYBY,
    PRE_ORDER
}

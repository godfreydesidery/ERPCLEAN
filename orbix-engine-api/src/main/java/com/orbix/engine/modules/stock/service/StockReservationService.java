package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.stock.domain.dto.ItemBranchBalanceDto;

import java.math.BigDecimal;

/**
 * Reservation port over the stock ledger (F7.2). Reservations are tracked
 * on {@code item_branch_balance.qty_reserved} (a parallel counter to
 * {@code qty_on_hand}) and audited via {@code stock_move} rows of
 * {@code move_type = RESERVED}.
 *
 * <p>Distinct from {@link StockMoveService#post} — a reservation does NOT
 * reduce {@code qty_on_hand}; it only locks part of it against another
 * outbound flow. Availability for downstream callers is
 * {@code qty_on_hand - qty_reserved}.
 *
 * <p>All write paths require the branch's business day to be OPEN.
 * Reservation moves carry the originating document on {@code ref_type} /
 * {@code ref_id} so the audit ledger is queryable per order.
 */
public interface StockReservationService {

    /**
     * Locks {@code qty} units of {@code itemId} at {@code branchId} against
     * the document identified by {@code (refType, refId)}. Throws if the
     * post-reservation availability would go negative. Writes a
     * {@code stock_move} of {@code move_type = RESERVED}, {@code qty = +qty}.
     */
    ItemBranchBalanceDto reserve(Long itemId, Long branchId, BigDecimal qty,
                                 String refType, Long refId, String notes);

    /**
     * Releases a previously locked reservation. Writes a compensating
     * {@code stock_move} of {@code move_type = RESERVED}, {@code qty = -qty}.
     * Decrements {@code qty_reserved}; never goes below zero.
     */
    ItemBranchBalanceDto release(Long itemId, Long branchId, BigDecimal qty,
                                 String refType, Long refId, String notes);

    /**
     * Available qty for new reservations / sales: {@code qty_on_hand - qty_reserved}.
     */
    BigDecimal available(Long itemId, Long branchId);
}

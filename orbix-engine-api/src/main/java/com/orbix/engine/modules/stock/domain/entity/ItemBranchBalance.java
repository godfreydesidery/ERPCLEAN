package com.orbix.engine.modules.stock.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Maintained cache of current stock per (item, branch) — rebuildable from
 * {@code stock_move}. Inbound moves recompute the moving-average cost; outbound
 * moves consume at the current average. DATA-MODEL.md §4.2.
 */
@Entity
@Table(name = "item_branch_balance")
@IdClass(ItemBranchBalanceId.class)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = {"itemId", "branchId"})
public class ItemBranchBalance {

    private static final int MONEY_SCALE = 4;

    @Id
    @Column(name = "item_id")
    private Long itemId;

    @Id
    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "qty_on_hand", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyOnHand = BigDecimal.ZERO;

    @Column(name = "qty_reserved", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyReserved = BigDecimal.ZERO;

    @Column(name = "qty_in_transit", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyInTransit = BigDecimal.ZERO;

    @Column(name = "avg_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal avgCost = BigDecimal.ZERO;

    @Column(name = "last_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal lastCost = BigDecimal.ZERO;

    @Column(name = "reorder_min", precision = 18, scale = 4)
    private BigDecimal reorderMin;

    @Column(name = "reorder_max", precision = 18, scale = 4)
    private BigDecimal reorderMax;

    @Column(name = "bin_location", length = 40)
    private String binLocation;

    @Column(name = "last_moved_at")
    private Instant lastMovedAt;

    public ItemBranchBalance(Long itemId, Long branchId) {
        this.itemId = itemId;
        this.branchId = branchId;
    }

    /** True if removing {@code qty} (a positive amount) would drive on-hand below zero. */
    public boolean wouldGoNegative(BigDecimal qty) {
        return qtyOnHand.subtract(qty).signum() < 0;
    }

    /** Applies an inbound move of {@code qty} units at {@code unitCost}, recomputing the moving average. */
    public void applyInbound(BigDecimal qty, BigDecimal unitCost, Instant at) {
        BigDecimal newQty = qtyOnHand.add(qty);
        if (qtyOnHand.signum() > 0 && newQty.signum() > 0) {
            this.avgCost = qtyOnHand.multiply(avgCost).add(qty.multiply(unitCost))
                .divide(newQty, MONEY_SCALE, RoundingMode.HALF_UP);
        } else {
            this.avgCost = unitCost;
        }
        this.lastCost = unitCost;
        this.qtyOnHand = newQty;
        this.lastMovedAt = at;
    }

    /** Applies an outbound move of {@code qty} units (a positive amount), consuming at the current average. */
    public void applyOutbound(BigDecimal qty, Instant at) {
        this.qtyOnHand = qtyOnHand.subtract(qty);
        this.lastMovedAt = at;
    }
}

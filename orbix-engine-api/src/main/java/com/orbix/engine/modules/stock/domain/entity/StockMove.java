package com.orbix.engine.modules.stock.domain.entity;

import com.orbix.engine.modules.stock.domain.enums.StockMoveDirection;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only ledger of every stock change — the source of truth for stock.
 * Immutable: no setters, no {@code updated_at} / {@code version}. All balances
 * are derived from sums over this table. DATA-MODEL.md §4.1.
 */
@Entity
@Table(name = "stock_move")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class StockMove {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stock_move_seq")
    @SequenceGenerator(name = "stock_move_seq", sequenceName = "stock_move_seq", allocationSize = 50)
    @Setter  // JPA assigns it; the business fields stay set-once via the constructor
    private Long id;

    @Column(nullable = false)
    private Instant at;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Signed: positive = inbound, negative = outbound. */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal qty;

    /** Unit cost at the time of the move. */
    @Column(name = "cost_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal costAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockMoveDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "move_type", nullable = false, length = 40)
    private StockMoveType moveType;

    @Column(name = "ref_type", nullable = false, length = 40)
    private String refType;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(length = 200)
    private String notes;

    /** Set when the move is attributed to a specific {@code stock_batch} row (batch-tracked items). */
    @Column(name = "batch_id")
    private Long batchId;

    @SuppressWarnings("java:S107")  // a posting row is inherently wide; a VO would only shuffle the args
    public StockMove(Instant at, Long itemId, Long branchId, Long companyId, BigDecimal qty,
                     BigDecimal costAmount, StockMoveType moveType, String refType, Long refId,
                     Long actorId, String notes, Long batchId) {
        this.at = at;
        this.itemId = itemId;
        this.branchId = branchId;
        this.companyId = companyId;
        this.qty = qty;
        this.costAmount = costAmount;
        this.direction = qty.signum() >= 0 ? StockMoveDirection.IN : StockMoveDirection.OUT;
        this.moveType = moveType;
        this.refType = refType;
        this.refId = refId;
        this.actorId = actorId;
        this.notes = notes;
        this.batchId = batchId;
    }
}

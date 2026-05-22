package com.orbix.engine.modules.production.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * BOM line (F7.3a). DATA-MODEL §9.2.
 *
 * <p>A line is EITHER a raw-material consumption ({@code input_item_id} set)
 * OR a sub-recipe reference ({@code sub_bom_id} set) — the BOM service rejects
 * lines with both or neither at activation.
 *
 * <p>{@code qty} is per-execution; the planner multiplies by the batch's
 * {@code planned_qty / bom.output_qty} to derive total required quantity.
 * {@code wastage_pct} bumps the planned consumption up to account for expected
 * process loss (e.g. flour spilled while mixing).
 */
@Entity
@Table(name = "bom_line",
    uniqueConstraints = @UniqueConstraint(name = "uk_bom_line_no",
        columnNames = {"bom_id", "line_no"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class BomLine {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bom_line_seq")
    @SequenceGenerator(name = "bom_line_seq", sequenceName = "bom_line_seq", allocationSize = 50)
    private Long id;

    @Column(name = "bom_id", nullable = false)
    private Long bomId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "input_item_id")
    private Long inputItemId;

    @Column(name = "sub_bom_id")
    private Long subBomId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal qty;

    @Column(name = "uom_id", nullable = false)
    private Long uomId;

    @Column(name = "wastage_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal wastagePct = BigDecimal.ZERO;

    @Column(length = 200)
    private String notes;

    @SuppressWarnings("java:S107")
    public BomLine(Long bomId, Integer lineNo, Long inputItemId, Long subBomId,
                   BigDecimal qty, Long uomId, BigDecimal wastagePct, String notes) {
        this.bomId = bomId;
        this.lineNo = lineNo;
        this.inputItemId = inputItemId;
        this.subBomId = subBomId;
        this.qty = qty;
        this.uomId = uomId;
        this.wastagePct = wastagePct != null ? wastagePct : BigDecimal.ZERO;
        this.notes = notes;
    }

    public boolean isSubBom() {
        return subBomId != null;
    }
}

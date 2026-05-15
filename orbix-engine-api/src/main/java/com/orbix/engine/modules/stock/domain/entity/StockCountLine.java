package com.orbix.engine.modules.stock.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One item line on a stock count. {@code systemQty} is frozen at count start. DATA-MODEL.md §4.4. */
@Entity
@Table(name = "stock_count_line")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class StockCountLine {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stock_count_line_seq")
    @SequenceGenerator(name = "stock_count_line_seq", sequenceName = "stock_count_line_seq",
        allocationSize = 50)
    private Long id;

    @Column(name = "stock_count_id", nullable = false)
    private Long stockCountId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "system_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal systemQty;

    @Column(name = "counted_qty", precision = 18, scale = 4)
    private BigDecimal countedQty;

    @Column(name = "variance_qty", precision = 18, scale = 4)
    private BigDecimal varianceQty;

    @Column(length = 200)
    private String note;

    public StockCountLine(Long stockCountId, Long itemId, BigDecimal systemQty) {
        this.stockCountId = stockCountId;
        this.itemId = itemId;
        this.systemQty = systemQty;
    }

    public void recordCount(BigDecimal countedQty, String note) {
        this.countedQty = countedQty;
        this.note = note;
    }

    /** variance = counted - system. Uncounted lines (null counted) resolve to zero variance. */
    public void computeVariance() {
        BigDecimal counted = countedQty != null ? countedQty : systemQty;
        this.varianceQty = counted.subtract(systemQty);
    }
}

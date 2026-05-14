package com.orbix.engine.modules.stock.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One item line on a transfer. {@code costAmount} is frozen at issue. DATA-MODEL.md §4.6. */
@Entity
@Table(name = "stock_transfer_line")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class StockTransferLine {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stock_transfer_line_seq")
    @SequenceGenerator(name = "stock_transfer_line_seq", sequenceName = "stock_transfer_line_seq",
        allocationSize = 50)
    private Long id;

    @Column(name = "stock_transfer_id", nullable = false)
    private Long stockTransferId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "issued_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal issuedQty;

    @Column(name = "received_qty", precision = 18, scale = 4)
    private BigDecimal receivedQty;

    @Column(name = "cost_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal costAmount = BigDecimal.ZERO;

    public StockTransferLine(Long stockTransferId, Long itemId, BigDecimal issuedQty) {
        this.stockTransferId = stockTransferId;
        this.itemId = itemId;
        this.issuedQty = issuedQty;
    }

    public void recordReceived(BigDecimal receivedQty) {
        this.receivedQty = receivedQty;
    }
}

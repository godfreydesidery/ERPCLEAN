package com.orbix.engine.modules.sales.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Packing-list line. DATA-MODEL.md §6.11. */
@Entity
@Table(name = "packing_list_line")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class PackingListLine {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "packing_list_line_seq")
    @SequenceGenerator(name = "packing_list_line_seq", sequenceName = "packing_list_line_seq",
        allocationSize = 50)
    private Long id;

    @Column(name = "packing_list_id", nullable = false)
    private Long packingListId;

    @Column(name = "sales_invoice_line_id", nullable = false)
    private Long salesInvoiceLineId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal qty;

    public PackingListLine(Long packingListId, Long salesInvoiceLineId, BigDecimal qty) {
        this.packingListId = packingListId;
        this.salesInvoiceLineId = salesInvoiceLineId;
        this.qty = qty;
    }
}

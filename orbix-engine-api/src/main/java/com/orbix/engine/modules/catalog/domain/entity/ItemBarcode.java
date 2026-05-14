package com.orbix.engine.modules.catalog.domain.entity;

import com.orbix.engine.modules.catalog.domain.enums.BarcodeType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A scannable barcode for an item. {@code barcode} is globally unique;
 * {@code packUomId} + {@code packQty} declare how many base units the
 * scanned pack represents (defaults to 1 of the item's own UoM).
 */
@Entity
@Table(name = "item_barcode", uniqueConstraints = @UniqueConstraint(name = "uk_item_barcode", columnNames = "barcode"))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class ItemBarcode {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "item_barcode_seq")
    @SequenceGenerator(name = "item_barcode_seq", sequenceName = "item_barcode_seq", allocationSize = 50)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false, length = 40)
    private String barcode;

    @Enumerated(EnumType.STRING)
    @Column(name = "barcode_type", nullable = false, length = 20)
    private BarcodeType barcodeType = BarcodeType.EAN13;

    @Column(name = "pack_uom_id")
    private Long packUomId;

    @Column(name = "pack_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal packQty = BigDecimal.ONE;

    public ItemBarcode(Long itemId, String barcode, BarcodeType barcodeType,
                       Long packUomId, BigDecimal packQty) {
        this.itemId = itemId;
        this.barcode = barcode;
        this.barcodeType = barcodeType != null ? barcodeType : BarcodeType.EAN13;
        this.packUomId = packUomId;
        this.packQty = packQty != null ? packQty : BigDecimal.ONE;
    }
}

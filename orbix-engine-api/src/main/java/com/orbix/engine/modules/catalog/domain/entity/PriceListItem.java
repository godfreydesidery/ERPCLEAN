package com.orbix.engine.modules.catalog.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A price for one item in one price list and UoM, valid over {@code [validFrom, validTo]}.
 * {@code validTo} null = the currently-effective price. Rows are never edited in place:
 * a price change closes the prior row and inserts a new one. DATA-MODEL.md §3.9.
 */
@Entity
@Table(name = "price_list_item")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class PriceListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "price_list_item_seq")
    @SequenceGenerator(name = "price_list_item_seq", sequenceName = "price_list_item_seq", allocationSize = 50)
    private Long id;

    @Column(name = "price_list_id", nullable = false)
    private Long priceListId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "uom_id", nullable = false)
    private Long uomId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    public PriceListItem(Long priceListId, Long itemId, Long uomId, BigDecimal price, LocalDate validFrom) {
        this.priceListId = priceListId;
        this.itemId = itemId;
        this.uomId = uomId;
        this.price = price;
        this.validFrom = validFrom;
    }

    /** Closes this price row the day before the replacement takes effect. */
    public void closeOn(LocalDate replacementEffectiveFrom) {
        this.validTo = replacementEffectiveFrom.minusDays(1);
    }
}

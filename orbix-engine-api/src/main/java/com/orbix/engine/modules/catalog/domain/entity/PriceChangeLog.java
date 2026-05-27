package com.orbix.engine.modules.catalog.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Append-only audit of every price change. Never updated, never deleted.
 * {@code newPrice} null records a discontinuation (the price was withdrawn).
 * DATA-MODEL.md §3.10.
 */
@Entity
@Table(name = "price_change_log")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class PriceChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "price_change_log_seq")
    @SequenceGenerator(name = "price_change_log_seq", sequenceName = "price_change_log_seq", allocationSize = 50)
    private Long id;

    @Column(name = "price_list_item_id", nullable = false)
    private Long priceListItemId;

    @Column(name = "old_price", precision = 18, scale = 4)
    private BigDecimal oldPrice;

    @Column(name = "new_price", precision = 18, scale = 4)
    private BigDecimal newPrice;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    @Column(columnDefinition = "TEXT")
    private String reason;

    public PriceChangeLog(Long priceListItemId, BigDecimal oldPrice, BigDecimal newPrice,
                          LocalDate effectiveFrom, Long changedBy, String reason) {
        this.priceListItemId = priceListItemId;
        this.oldPrice = oldPrice;
        this.newPrice = newPrice;
        this.effectiveFrom = effectiveFrom;
        this.changedAt = Instant.now();
        this.changedBy = changedBy;
        this.reason = reason;
    }
}

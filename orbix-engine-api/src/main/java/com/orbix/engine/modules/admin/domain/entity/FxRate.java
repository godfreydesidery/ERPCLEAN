package com.orbix.engine.modules.admin.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** Daily FX rate snapshot. Most recent rate ≤ sale time wins. DATA-MODEL.md §17.3. */
@Entity
@Table(name = "fx_rate")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class FxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fx_rate_seq")
    @SequenceGenerator(name = "fx_rate_seq", sequenceName = "fx_rate_seq", allocationSize = 50)
    private Long id;

    @Column(name = "from_currency", nullable = false, length = 3)
    private String fromCurrency;

    @Column(name = "to_currency", nullable = false, length = 3)
    private String toCurrency;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal rate;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public FxRate(String fromCurrency, String toCurrency, BigDecimal rate, Instant effectiveAt, Long actorId) {
        this.fromCurrency = fromCurrency;
        this.toCurrency = toCurrency;
        this.rate = rate;
        this.effectiveAt = effectiveAt;
        this.createdBy = actorId;
        this.createdAt = Instant.now();
    }
}

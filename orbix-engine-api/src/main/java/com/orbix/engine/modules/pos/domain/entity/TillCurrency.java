package com.orbix.engine.modules.pos.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A foreign currency a {@link Till} accepts as FX tender (F5.6). The company's
 * functional currency is implicit and not stored here. DATA-MODEL.md §17.4.
 */
@Entity
@Table(name = "till_currency")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class TillCurrency {

    @EmbeddedId
    private TillCurrencyId id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    public TillCurrency(Long tillId, String currencyCode, Long actorId) {
        this.id = new TillCurrencyId(tillId, currencyCode);
        this.createdAt = Instant.now();
        this.createdBy = actorId;
    }
}

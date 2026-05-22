package com.orbix.engine.modules.pos.domain.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Composite key for {@link TillCurrency}. DATA-MODEL.md §17.4. */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TillCurrencyId implements Serializable {

    private Long tillId;
    private String currencyCode;
}

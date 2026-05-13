package com.orbix.engine.modules.admin.domain.entity;

import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** ISO 4217 currency registry. PK is the 3-letter code. DATA-MODEL.md §17.2. */
@Entity
@Table(name = "currency")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "code")
public class Currency {

    @Id
    @Column(length = 3)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 8)
    private String symbol;

    @Column(name = "minor_unit_digits", nullable = false)
    private int minorUnitDigits = 2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdminStatus status = AdminStatus.ACTIVE;

    public Currency(String code, String name, String symbol, int minorUnitDigits) {
        this.code = code;
        this.name = name;
        this.symbol = symbol;
        this.minorUnitDigits = minorUnitDigits;
    }
}

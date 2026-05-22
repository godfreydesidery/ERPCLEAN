package com.orbix.engine.modules.catalog.domain.entity;

import com.orbix.engine.modules.catalog.domain.enums.UomDimension;
import com.orbix.engine.modules.common.domain.entity.UidEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Unit of measure. Global (not company-scoped) — the one shared catalog table.
 * See the catalog README §3.
 */
@Entity
@Table(
    name = "uom",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_uom_uid",  columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_uom_code", columnNames = {"code"})
    }
)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class Uom extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "uom_seq")
    @SequenceGenerator(name = "uom_seq", sequenceName = "uom_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UomDimension dimension;

    @Column(name = "is_base", nullable = false)
    private boolean base;

    public Uom(String code, String name, UomDimension dimension, boolean base) {
        this.code = code;
        this.name = name;
        this.dimension = dimension;
        this.base = base;
    }

    public void update(String name, UomDimension dimension, boolean base) {
        this.name = name;
        this.dimension = dimension;
        this.base = base;
    }
}

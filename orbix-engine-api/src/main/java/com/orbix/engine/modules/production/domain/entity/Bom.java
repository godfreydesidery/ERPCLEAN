package com.orbix.engine.modules.production.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.production.domain.enums.BomStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Bill-of-materials header (F7.3a). DATA-MODEL §9.1 + Phase 1.1 additions.
 *
 * <p>One BOM = one output item × one output qty per execution, at one version.
 * Multiple versions of the same recipe coexist via the
 * {@code (output_item_id, version)} UNIQUE; the {@code version} endpoint
 * clones the current ACTIVE row into a new DRAFT and retires the old at
 * {@code valid_from − 1} day on activation.
 */
@Entity
@Table(name = "bom",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_bom_uid", columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_bom_output_version",
            columnNames = {"output_item_id", "version"})
    })
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class Bom extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bom_seq")
    @SequenceGenerator(name = "bom_seq", sequenceName = "bom_seq", allocationSize = 50)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    /** Legacy hierarchical link — mostly null. Operational sub-recipe link is {@code bom_line.sub_bom_id}. */
    @Column(name = "parent_bom_id")
    private Long parentBomId;

    @Column(name = "output_item_id", nullable = false)
    private Long outputItemId;

    @Column(name = "output_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal outputQty;

    @Column(name = "output_uom_id", nullable = false)
    private Long outputUomId;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    /** Expected good output / theoretical output; default 100 = no loss. */
    @Column(name = "standard_yield_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal standardYieldPct = new BigDecimal("100");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BomStatus status = BomStatus.DRAFT;

    @Column(length = 2000)
    private String notes;

    @Version
    @Column(name = "version_no")
    private Integer versionNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @SuppressWarnings("java:S107")
    public Bom(Long companyId, Long sectionId, Long parentBomId, Long outputItemId,
               BigDecimal outputQty, Long outputUomId, Integer version, LocalDate validFrom,
               BigDecimal standardYieldPct, String notes, Long actorId) {
        this.companyId = companyId;
        this.sectionId = sectionId;
        this.parentBomId = parentBomId;
        this.outputItemId = outputItemId;
        this.outputQty = outputQty;
        this.outputUomId = outputUomId;
        this.version = version;
        this.validFrom = validFrom;
        this.standardYieldPct = standardYieldPct != null ? standardYieldPct : new BigDecimal("100");
        this.notes = notes;
        this.status = BomStatus.DRAFT;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void activate(Long actorId) {
        if (status != BomStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT BOMs can be activated (was " + status + ")");
        }
        this.status = BomStatus.ACTIVE;
        touch(actorId);
    }

    public void retire(LocalDate retireDate, Long actorId) {
        if (status == BomStatus.RETIRED) {
            throw new IllegalStateException("BOM is already RETIRED");
        }
        this.status = BomStatus.RETIRED;
        this.validTo = retireDate;
        touch(actorId);
    }

    /** Audited rename / yield / notes edit while DRAFT only. */
    public void editHeader(BigDecimal outputQty, Long outputUomId, BigDecimal standardYieldPct,
                           String notes, LocalDate validFrom, Long actorId) {
        if (status != BomStatus.DRAFT) {
            throw new IllegalStateException("BOM header is only editable while DRAFT (was " + status + ")");
        }
        if (outputQty != null) this.outputQty = outputQty;
        if (outputUomId != null) this.outputUomId = outputUomId;
        if (standardYieldPct != null) this.standardYieldPct = standardYieldPct;
        if (notes != null) this.notes = notes;
        if (validFrom != null) this.validFrom = validFrom;
        touch(actorId);
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}

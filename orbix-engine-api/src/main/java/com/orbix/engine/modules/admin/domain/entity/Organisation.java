package com.orbix.engine.modules.admin.domain.entity;

import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Top of the org → company → branch hierarchy. One per deployment. DATA-MODEL.md §1.1. */
@Entity
@Table(name = "organisation")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class Organisation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "organisation_seq")
    @SequenceGenerator(name = "organisation_seq", sequenceName = "organisation_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdminStatus status = AdminStatus.ACTIVE;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "updated_by", nullable = false) private Long updatedBy;

    public Organisation(String name, String legalName, String currencyCode, String countryCode, Long actorId) {
        this.name = name;
        this.legalName = legalName;
        this.currencyCode = currencyCode;
        this.countryCode = countryCode;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }
}

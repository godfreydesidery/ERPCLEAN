package com.orbix.engine.modules.party.domain.entity;

import com.orbix.engine.modules.common.domain.Pii;
import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The unified person / organisation record. One row per real-world entity;
 * customer / supplier / employee / sales-agent roles attach via shared-PK
 * role tables (so the role tables don't need their own uid — Party's uid
 * is the canonical external identifier for them too). Adding a role to a
 * party with a matching TIN reuses this row rather than duplicating it.
 * DATA-MODEL.md §2.1.
 */
@Entity
@Table(
    name = "party",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_party_uid",          columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_party_company_code", columnNames = {"company_id", "code"})
    }
)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class Party extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "party_seq")
    @SequenceGenerator(name = "party_seq", sequenceName = "party_seq", allocationSize = 50)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 40)
    private String code;

    @Pii
    @Column(nullable = false, length = 200)
    private String name;

    @Pii
    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PartyCategory category;

    @Column(length = 40)
    private String tin;

    @Column(length = 40)
    private String vrn;

    @Pii
    @Column(length = 40)
    private String phone;

    @Pii
    @Column(length = 120)
    private String email;

    @Pii
    @Column(name = "physical_address", columnDefinition = "TEXT")
    private String physicalAddress;

    @Pii
    @Column(name = "postal_address", columnDefinition = "TEXT")
    private String postalAddress;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PartyStatus status = PartyStatus.ACTIVE;

    /**
     * Monotonic sync cursor stamp — set by SyncChangeSeqService on every
     * customer create/update/archive. Drives the 'customer' dataset in
     * /sync/pull and /sync/bootstrap. NULL = row pre-dates sync feature
     * (treated as seq=0 by the pull query).
     */
    @Column(name = "change_seq")
    private Long changeSeq;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "updated_by", nullable = false) private Long updatedBy;

    public Party(Long companyId, String code, String name, PartyCategory category, Long actorId) {
        this.companyId = companyId;
        this.code = code;
        this.name = name;
        this.category = category;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    @SuppressWarnings("java:S107")  // mirrors the spec column set; a VO would cost more than it saves
    public void updateDetails(String name, String legalName, PartyCategory category, String tin,
                              String vrn, String phone, String email, String physicalAddress,
                              String postalAddress, String countryCode, String notes, Long actorId) {
        this.name = name;
        this.legalName = legalName;
        this.category = category;
        this.tin = tin;
        this.vrn = vrn;
        this.phone = phone;
        this.email = email;
        this.physicalAddress = physicalAddress;
        this.postalAddress = postalAddress;
        this.countryCode = countryCode;
        this.notes = notes;
        touch(actorId);
    }

    public void archive(Long actorId) {
        this.status = PartyStatus.ARCHIVED;
        touch(actorId);
    }

    public void activate(Long actorId) {
        this.status = PartyStatus.ACTIVE;
        touch(actorId);
    }

    public void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}

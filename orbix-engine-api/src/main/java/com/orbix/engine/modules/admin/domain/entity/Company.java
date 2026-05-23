package com.orbix.engine.modules.admin.domain.entity;

import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Legal entity within an organisation. DATA-MODEL.md §1.2. */
@Entity
@Table(
    name = "company",
    uniqueConstraints = @UniqueConstraint(name = "uk_company_org_code", columnNames = {"organisation_id", "code"})
)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "company_seq")
    @SequenceGenerator(name = "company_seq", sequenceName = "company_seq", allocationSize = 50)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(length = 40)
    private String tin;

    @Column(length = 40)
    private String vrn;

    @Column(name = "physical_address", columnDefinition = "TEXT")
    private String physicalAddress;

    @Column(name = "postal_address", columnDefinition = "TEXT")
    private String postalAddress;

    @Column(length = 40)
    private String phone;

    @Column(length = 120)
    private String email;

    @Column(length = 200)
    private String website;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "logo_object_key", length = 200)
    private String logoObjectKey;

    @Column(name = "default_invoice_note", columnDefinition = "TEXT")
    private String defaultInvoiceNote;

    @Column(name = "default_quotation_note", columnDefinition = "TEXT")
    private String defaultQuotationNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdminStatus status = AdminStatus.ACTIVE;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "updated_by", nullable = false) private Long updatedBy;

    public Company(Long organisationId, String code, String name,
                   String currencyCode, String countryCode, String timeZone, Long actorId) {
        this.organisationId = organisationId;
        this.code = code;
        this.name = name;
        this.currencyCode = currencyCode;
        this.countryCode = countryCode;
        this.timeZone = timeZone;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    /** Edit the company profile (US-COMP-002). The immutable {@code code} is not touched. */
    public void updateProfile(String name, String legalName, String tin, String vrn,
                              String physicalAddress, String postalAddress, String phone,
                              String email, String website, String currencyCode,
                              String countryCode, String timeZone,
                              String defaultInvoiceNote, String defaultQuotationNote, Long actorId) {
        this.name = name;
        this.legalName = legalName;
        this.tin = tin;
        this.vrn = vrn;
        this.physicalAddress = physicalAddress;
        this.postalAddress = postalAddress;
        this.phone = phone;
        this.email = email;
        this.website = website;
        this.currencyCode = currencyCode;
        this.countryCode = countryCode;
        this.timeZone = timeZone;
        this.defaultInvoiceNote = defaultInvoiceNote;
        this.defaultQuotationNote = defaultQuotationNote;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}

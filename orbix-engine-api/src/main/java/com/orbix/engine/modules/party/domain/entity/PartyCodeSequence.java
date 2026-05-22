package com.orbix.engine.modules.party.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Per-company, per-prefix counter for auto-generated party codes. Read,
 * incremented, and saved under optimistic locking by {@code PartyService}.
 * One row per (company_id, prefix); created lazily on first use.
 */
@Entity
@Table(
    name = "party_code_sequence",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_party_code_seq_company_prefix",
        columnNames = {"company_id", "prefix"})
)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class PartyCodeSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "party_code_sequence_seq")
    @SequenceGenerator(name = "party_code_sequence_seq", sequenceName = "party_code_sequence_seq",
        allocationSize = 50)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 10)
    private String prefix;

    @Column(name = "current_value", nullable = false)
    private long currentValue;

    @Version
    private Integer version;

    public PartyCodeSequence(Long companyId, String prefix) {
        this.companyId = companyId;
        this.prefix = prefix;
        this.currentValue = 0L;
    }
}

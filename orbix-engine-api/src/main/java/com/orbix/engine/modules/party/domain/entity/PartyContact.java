package com.orbix.engine.modules.party.domain.entity;

import com.orbix.engine.modules.common.domain.Pii;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** A named contact person at a (usually business) party. DATA-MODEL.md §2.3. */
@Entity
@Table(name = "party_contact")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class PartyContact {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "party_contact_seq")
    @SequenceGenerator(name = "party_contact_seq", sequenceName = "party_contact_seq", allocationSize = 50)
    private Long id;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Pii
    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "role_label", length = 80)
    private String roleLabel;

    @Pii
    @Column(length = 40)
    private String phone;

    @Pii
    @Column(length = 120)
    private String email;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    public PartyContact(Long partyId, String name) {
        this.partyId = partyId;
        this.name = name;
    }
}

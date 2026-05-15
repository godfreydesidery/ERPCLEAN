package com.orbix.engine.modules.party.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One of a party's addresses (delivery / billing / office). DATA-MODEL.md §2.2. */
@Entity
@Table(name = "party_address")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class PartyAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "party_address_seq")
    @SequenceGenerator(name = "party_address_seq", sequenceName = "party_address_seq", allocationSize = 50)
    private Long id;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(nullable = false, length = 40)
    private String label;

    @Column(nullable = false, length = 200)
    private String line1;

    @Column(length = 200)
    private String line2;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String region;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "gps_lat", precision = 10, scale = 7)
    private BigDecimal gpsLat;

    @Column(name = "gps_lng", precision = 10, scale = 7)
    private BigDecimal gpsLng;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    public PartyAddress(Long partyId, String label, String line1) {
        this.partyId = partyId;
        this.label = label;
        this.line1 = line1;
    }
}

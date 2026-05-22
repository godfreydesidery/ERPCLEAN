package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.party.domain.dto.PartyDetailsDto;
import com.orbix.engine.modules.party.domain.dto.PartyResponseDto;
import com.orbix.engine.modules.party.domain.entity.Party;

import java.util.Optional;

/**
 * Shared party operations consumed by the role services (customer / supplier /
 * employee / sales-agent). Implements the shared-party rule: a new role for a
 * party whose TIN already exists reuses that party rather than duplicating it.
 */
public interface PartyService {

    /** The existing party in the caller's company carrying this TIN, if any. */
    Optional<PartyResponseDto> findByTin(String tin);

    /**
     * Returns the existing company party with {@code details.tin()} (shared-party
     * rule) or creates a fresh one with {@code code}. Existing parties are
     * returned untouched.
     */
    Party resolveOrCreate(String code, PartyDetailsDto details, Long actorId);

    /** Applies editable detail fields to a managed party. */
    void applyDetails(Party party, PartyDetailsDto details, Long actorId);

    /** Loads a party, asserting it belongs to the caller's company. */
    Party requireInCompany(Long partyId);

    /** Marks the party (and therefore every role on it) INACTIVE. */
    void deactivate(Long partyId);
}

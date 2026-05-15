package com.orbix.engine.modules.party.repository;

import com.orbix.engine.modules.party.domain.entity.PartyContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartyContactRepository extends JpaRepository<PartyContact, Long> {

    List<PartyContact> findByPartyId(Long partyId);
}

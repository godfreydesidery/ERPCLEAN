package com.orbix.engine.modules.party.repository;

import com.orbix.engine.modules.party.domain.entity.PartyAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartyAddressRepository extends JpaRepository<PartyAddress, Long> {

    List<PartyAddress> findByPartyId(Long partyId);
}

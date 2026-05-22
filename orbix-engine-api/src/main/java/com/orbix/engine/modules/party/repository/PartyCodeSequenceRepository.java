package com.orbix.engine.modules.party.repository;

import com.orbix.engine.modules.party.domain.entity.PartyCodeSequence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PartyCodeSequenceRepository extends JpaRepository<PartyCodeSequence, Long> {

    Optional<PartyCodeSequence> findByCompanyIdAndPrefix(Long companyId, String prefix);
}

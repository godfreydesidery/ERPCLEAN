package com.orbix.engine.modules.party.repository;

import com.orbix.engine.modules.party.domain.entity.Party;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartyRepository extends JpaRepository<Party, Long> {

    Optional<Party> findByUid(String uid);

    Optional<Party> findByCompanyIdAndCode(Long companyId, String code);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    /** Shared-party lookup: an existing party in the company with this TIN, if any. */
    Optional<Party> findFirstByCompanyIdAndTinAndTinNotNull(Long companyId, String tin);

    List<Party> findByCompanyId(Long companyId);
}

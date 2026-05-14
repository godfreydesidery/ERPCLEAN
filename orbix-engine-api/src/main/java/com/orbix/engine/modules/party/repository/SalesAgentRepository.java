package com.orbix.engine.modules.party.repository;

import com.orbix.engine.modules.party.domain.entity.SalesAgent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalesAgentRepository extends JpaRepository<SalesAgent, Long> {

    @Query("select a from SalesAgent a join Party p on p.id = a.partyId where p.companyId = :companyId")
    List<SalesAgent> findByCompanyId(@Param("companyId") Long companyId);

    @Query("""
        select count(a) > 0 from SalesAgent a join Party p on p.id = a.partyId
        where p.companyId = :companyId and a.agentCode = :code
        """)
    boolean existsByCompanyIdAndAgentCode(@Param("companyId") Long companyId,
                                          @Param("code") String code);
}

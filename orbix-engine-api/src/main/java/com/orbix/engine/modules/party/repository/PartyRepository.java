package com.orbix.engine.modules.party.repository;

import com.orbix.engine.modules.party.domain.entity.Party;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PartyRepository extends JpaRepository<Party, Long> {

    Optional<Party> findByUid(String uid);

    Optional<Party> findByCompanyIdAndCode(Long companyId, String code);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    /** Shared-party lookup: an existing party in the company with this TIN, if any. */
    Optional<Party> findFirstByCompanyIdAndTinAndTinNotNull(Long companyId, String tin);

    List<Party> findByCompanyId(Long companyId);

    /**
     * Sync pull for the 'customer' dataset.
     * Returns Party rows that (a) belong to the company, (b) have a Customer role row,
     * and (c) have change_seq > fromSeq (or are NULL, treated as 0 on first pull).
     * Paged so the cursor can advance incrementally.
     */
    /**
     * Sync pull for the 'customer' dataset.
     * Returns Party rows that (a) belong to the company, (b) have a Customer role row,
     * and (c) have change_seq > fromSeq (or change_seq IS NULL, i.e. pre-dates sync).
     * On the first pull (fromSeq=0) NULL rows are included so they surface to the POS.
     * Paged so the cursor can advance incrementally.
     */
    @Query("""
        select p from Party p
        where p.companyId = :companyId
          and exists (select 1 from Customer c where c.partyId = p.id)
          and (p.changeSeq is null or p.changeSeq > :fromSeq)
        order by p.id asc
        """)
    List<Party> findCustomerPartiesByCompanyIdAndChangeSeqGreaterThan(
        @Param("companyId") Long companyId,
        @Param("fromSeq") long fromSeq,
        Pageable pageable);
}

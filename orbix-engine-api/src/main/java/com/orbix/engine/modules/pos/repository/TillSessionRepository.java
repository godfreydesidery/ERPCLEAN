package com.orbix.engine.modules.pos.repository;

import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TillSessionRepository extends JpaRepository<TillSession, Long> {

    Optional<TillSession> findByUid(String uid);

    Optional<TillSession> findFirstByTillIdAndStatus(Long tillId, TillSessionStatus status);

    /** Idempotency lookup for device-outbox sync (pre-check before insert). */
    Optional<TillSession> findByCompanyIdAndClientOpId(Long companyId, String clientOpId);

    List<TillSession> findByCompanyIdOrderByIdDesc(Long companyId);

    List<TillSession> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    List<TillSession> findByTillIdOrderByIdDesc(Long tillId);

    /** F7.5 EOD gate — returns any non-RECONCILED sessions for the close window. */
    List<TillSession> findByBranchIdAndBusinessDateAndStatusIn(
        Long branchId, LocalDate businessDate, List<TillSessionStatus> statuses);

    /**
     * F8.2 / US-RPT-003 — every till session whose business_date falls in
     * {@code [from, to]} (inclusive) for the audit's Z-history view.
     */
    List<TillSession> findByCompanyIdAndBusinessDateBetweenOrderByIdDesc(
        Long companyId, LocalDate from, LocalDate to);

    List<TillSession> findByCompanyIdAndBranchIdAndBusinessDateBetweenOrderByIdDesc(
        Long companyId, Long branchId, LocalDate from, LocalDate to);

    /**
     * Guard for branch/section deactivation (F5.1 / F7.3): counts OPEN sessions
     * whose till belongs to the given branch.  Tills are branch-scoped (no
     * section FK in v1), so this query covers both the branch guard and the
     * section guard (a section cannot be deactivated while any till in its
     * branch is open).  A non-zero result blocks deactivation.
     */
    @Query("""
        select count(s) from TillSession s
        join Till t on t.id = s.tillId
        where t.branchId = :branchId
          and s.status = :status
        """)
    long countByBranchIdAndStatus(
        @Param("branchId") Long branchId,
        @Param("status") TillSessionStatus status);
}

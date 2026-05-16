package com.orbix.engine.modules.pos.repository;

import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TillSessionRepository extends JpaRepository<TillSession, Long> {

    Optional<TillSession> findFirstByTillIdAndStatus(Long tillId, TillSessionStatus status);

    List<TillSession> findByCompanyIdOrderByIdDesc(Long companyId);

    List<TillSession> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    List<TillSession> findByTillIdOrderByIdDesc(Long tillId);

    /** F7.5 EOD gate — returns any non-RECONCILED sessions for the close window. */
    List<TillSession> findByBranchIdAndBusinessDateAndStatusIn(
        Long branchId, LocalDate businessDate, List<TillSessionStatus> statuses);
}

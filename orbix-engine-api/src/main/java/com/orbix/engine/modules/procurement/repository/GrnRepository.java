package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.Grn;
import com.orbix.engine.modules.procurement.domain.enums.GrnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GrnRepository extends JpaRepository<Grn, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    Optional<Grn> findByUid(String uid);

    List<Grn> findByCompanyIdOrderByIdDesc(Long companyId);

    List<Grn> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    Page<Grn> findByCompanyIdOrderByIdDesc(Long companyId, Pageable pageable);

    Page<Grn> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId, Pageable pageable);

    /** F7.5 EOD gate — open DRAFTs leak across days, so block close until cleared. */
    List<Grn> findByBranchIdAndStatus(Long branchId, GrnStatus status);

    /**
     * F8.2 / US-RPT-002 — POSTED GRNs for a branch on a given received date.
     * Used by the daily summary to roll up total purchases. {@code branchId}
     * = null scopes the whole company.
     */
    List<Grn> findByCompanyIdAndBranchIdAndReceivedDateAndStatus(
        Long companyId, Long branchId, LocalDate receivedDate, GrnStatus status);

    List<Grn> findByCompanyIdAndReceivedDateAndStatus(
        Long companyId, LocalDate receivedDate, GrnStatus status);
}

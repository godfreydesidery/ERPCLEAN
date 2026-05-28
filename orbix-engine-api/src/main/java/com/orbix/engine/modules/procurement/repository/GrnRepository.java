package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.Grn;
import com.orbix.engine.modules.procurement.domain.enums.GrnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GrnRepository extends JpaRepository<Grn, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    /** Used to gate APPROVED-LPO cancel: refuse if any GRN already drew against the LPO. */
    boolean existsByLpoOrderId(Long lpoOrderId);

    /** Lists every GRN for a given LPO, oldest first. Used on POSTED-cancel to rewind LPO state. */
    List<Grn> findByLpoOrderIdOrderByIdAsc(Long lpoOrderId);

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

    /**
     * GRN picker — filtered list for the vendor-return create form.
     * Both {@code supplierId} and {@code status} are optional (null = no filter on that dimension).
     * {@code branchId} is the scoped branch from {@code BranchScope}; null = company-wide.
     */
    @Query("""
        select g from Grn g
         where g.companyId = :companyId
           and (:branchId   is null or g.branchId   = :branchId)
           and (:supplierId is null or g.supplierId  = :supplierId)
           and (:status     is null or g.status      = :status)
         order by g.id desc
        """)
    Page<Grn> findFiltered(
        @Param("companyId")  Long companyId,
        @Param("branchId")   Long branchId,
        @Param("supplierId") Long supplierId,
        @Param("status")     GrnStatus status,
        Pageable pageable);
}

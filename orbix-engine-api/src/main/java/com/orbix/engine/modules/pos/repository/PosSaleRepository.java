package com.orbix.engine.modules.pos.repository;

import com.orbix.engine.modules.pos.domain.entity.PosSale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PosSaleRepository extends JpaRepository<PosSale, Long> {

    Optional<PosSale> findByUid(String uid);

    /** Idempotency lookup: the same client_op_id pushed twice must return the original. */
    Optional<PosSale> findByCompanyIdAndClientOpId(Long companyId, String clientOpId);

    boolean existsByCompanyIdAndNumber(Long companyId, String number);

    List<PosSale> findByCompanyIdOrderByIdDesc(Long companyId);

    List<PosSale> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    List<PosSale> findByTillSessionIdOrderByIdAsc(Long tillSessionId);

    /**
     * F8.2 / US-RPT-001 — every pos_sale (any kind, any status) on a given
     * business date scoped to {@code branchId} (null = company-wide). The
     * report differentiates SALE vs REFUND and POSTED vs VOIDED on the
     * service-layer aggregation rather than at the query level.
     */
    List<PosSale> findByCompanyIdAndBranchIdAndBusinessDateOrderByIdAsc(
        Long companyId, Long branchId, LocalDate businessDate);

    List<PosSale> findByCompanyIdAndBusinessDateOrderByIdAsc(Long companyId, LocalDate businessDate);
}

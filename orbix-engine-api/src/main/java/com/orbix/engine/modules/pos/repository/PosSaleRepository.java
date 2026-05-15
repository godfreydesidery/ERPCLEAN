package com.orbix.engine.modules.pos.repository;

import com.orbix.engine.modules.pos.domain.entity.PosSale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PosSaleRepository extends JpaRepository<PosSale, Long> {

    /** Idempotency lookup: the same client_op_id pushed twice must return the original. */
    Optional<PosSale> findByCompanyIdAndClientOpId(Long companyId, String clientOpId);

    boolean existsByCompanyIdAndNumber(Long companyId, String number);

    List<PosSale> findByCompanyIdOrderByIdDesc(Long companyId);

    List<PosSale> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    List<PosSale> findByTillSessionIdOrderByIdAsc(Long tillSessionId);
}

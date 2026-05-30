package com.orbix.engine.modules.fiscal.repository;

import com.orbix.engine.modules.fiscal.domain.entity.FiscalReceipt;
import com.orbix.engine.modules.fiscal.domain.enums.FiscalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for the FiscalReceipt aggregate. */
public interface FiscalReceiptRepository extends JpaRepository<FiscalReceipt, Long> {

    /** External-facing lookup by ULID uid. */
    Optional<FiscalReceipt> findByUid(String uid);

    /** One-to-one: each POS sale has at most one FiscalReceipt. */
    Optional<FiscalReceipt> findByPosSaleId(Long posSaleId);

    /** Operational queue: all receipts in a given status for a company (for monitoring). */
    List<FiscalReceipt> findByCompanyIdAndStatusOrderByCreatedAtAsc(Long companyId, FiscalStatus status);

    /** Operational queue for a branch. */
    List<FiscalReceipt> findByBranchIdAndStatusOrderByCreatedAtAsc(Long branchId, FiscalStatus status);
}

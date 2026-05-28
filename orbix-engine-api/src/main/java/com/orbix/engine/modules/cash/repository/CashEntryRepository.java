package com.orbix.engine.modules.cash.repository;

import com.orbix.engine.modules.cash.domain.entity.CashEntry;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CashEntryRepository extends JpaRepository<CashEntry, Long> {

    /** External lookup by ULID (URL handle). Append-only — no write counterpart. */
    Optional<CashEntry> findByUid(String uid);

    /** Idempotency probe — producer checks before inserting. */
    Optional<CashEntry> findByRefTypeAndRefIdAndDirection(String refType, Long refId, CashDirection direction);

    List<CashEntry> findByBranchIdAndBusinessDateOrderByAtAsc(Long branchId, LocalDate businessDate);

    List<CashEntry> findByBranchIdAndAccountAndBusinessDateOrderByAtAsc(
        Long branchId, CashAccount account, LocalDate businessDate);

    List<CashEntry> findByCompanyIdAndBusinessDateOrderByAtAsc(Long companyId, LocalDate businessDate);
}

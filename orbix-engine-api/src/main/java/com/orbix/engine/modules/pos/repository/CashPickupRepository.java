package com.orbix.engine.modules.pos.repository;

import com.orbix.engine.modules.pos.domain.entity.CashPickup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CashPickupRepository extends JpaRepository<CashPickup, Long> {

    /** External lookup by ULID (URL handle). */
    Optional<CashPickup> findByUid(String uid);

    /** Idempotency lookup for device-outbox sync (pre-check before insert). */
    Optional<CashPickup> findByCompanyIdAndClientOpId(Long companyId, String clientOpId);

    List<CashPickup> findByTillSessionIdOrderByAtAsc(Long tillSessionId);

    /** Sum of all pickups taken from a till session — used by close-till reconciliation. */
    default BigDecimal sumForSession(Long tillSessionId) {
        return findByTillSessionIdOrderByAtAsc(tillSessionId).stream()
            .map(CashPickup::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

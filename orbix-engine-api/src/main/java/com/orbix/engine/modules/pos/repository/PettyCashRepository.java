package com.orbix.engine.modules.pos.repository;

import com.orbix.engine.modules.pos.domain.entity.PettyCash;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface PettyCashRepository extends JpaRepository<PettyCash, Long> {

    List<PettyCash> findByTillSessionIdOrderByAtAsc(Long tillSessionId);

    /** Sum of all petty-cash payouts from a till session — used by close-till reconciliation. */
    default BigDecimal sumForSession(Long tillSessionId) {
        return findByTillSessionIdOrderByAtAsc(tillSessionId).stream()
            .map(PettyCash::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

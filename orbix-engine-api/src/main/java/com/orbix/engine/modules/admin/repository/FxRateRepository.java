package com.orbix.engine.modules.admin.repository;

import com.orbix.engine.modules.admin.domain.entity.FxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    /** Full quote history, newest first — backs the admin rate-history table.
     *  Tie-broken by id desc so same-instant quotes have a stable order. */
    List<FxRate> findAllByOrderByEffectiveAtDescIdDesc();

    /** Most recent rate with effective_at &lt;= the supplied time. Used at POS tender step.
     *  Tie-broken by id desc so the latest-quoted row wins when effective_at is equal. */
    @Query("""
        select r from FxRate r
        where r.fromCurrency = :from
          and r.toCurrency   = :to
          and r.effectiveAt <= :at
        order by r.effectiveAt desc, r.id desc
        limit 1
        """)
    Optional<FxRate> findMostRecent(@Param("from") String fromCurrency,
                                    @Param("to") String toCurrency,
                                    @Param("at") Instant at);
}

package com.orbix.engine.modules.giftcard.repository;

import com.orbix.engine.modules.giftcard.domain.entity.GiftCard;
import com.orbix.engine.modules.giftcard.domain.enums.GiftCardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GiftCardRepository extends JpaRepository<GiftCard, Long> {

    Optional<GiftCard> findByCode(String code);

    boolean existsByCode(String code);

    /** Used by the scheduled expiry job. */
    List<GiftCard> findByStatusAndExpiresAtBefore(GiftCardStatus status, Instant cutoff);

    /**
     * F8.5 / US-RPT-013 — outstanding-liability rollup for the accountant.
     * Groups gift cards by {@code (status, currency_code, issued_by_branch_id)}
     * and sums {@code current_balance}; returns
     * {@code Object[]{status, currencyCode, branchId, sumBalance, cardCount}}.
     * Filters by company always; optional branch + cutoff (issued_at < cutoff)
     * — the cutoff lets a finance report scope to a period close.
     */
    @Query("""
        select g.status, g.currencyCode, g.issuedByBranchId,
               coalesce(sum(g.currentBalance), 0), count(g.id)
          from GiftCard g
         where g.companyId = :companyId
           and (:branchId is null or g.issuedByBranchId = :branchId)
           and (:asOf is null or g.issuedAt <= :asOf)
         group by g.status, g.currencyCode, g.issuedByBranchId
        """)
    List<Object[]> aggregateLiability(@Param("companyId") Long companyId,
                                       @Param("branchId") Long branchId,
                                       @Param("asOf") Instant asOf);
}

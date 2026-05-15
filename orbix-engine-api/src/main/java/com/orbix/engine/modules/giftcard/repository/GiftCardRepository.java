package com.orbix.engine.modules.giftcard.repository;

import com.orbix.engine.modules.giftcard.domain.entity.GiftCard;
import com.orbix.engine.modules.giftcard.domain.enums.GiftCardStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GiftCardRepository extends JpaRepository<GiftCard, Long> {

    Optional<GiftCard> findByCode(String code);

    boolean existsByCode(String code);

    /** Used by the scheduled expiry job. */
    List<GiftCard> findByStatusAndExpiresAtBefore(GiftCardStatus status, Instant cutoff);
}

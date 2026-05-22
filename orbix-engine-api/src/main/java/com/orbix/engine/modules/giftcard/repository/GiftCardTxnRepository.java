package com.orbix.engine.modules.giftcard.repository;

import com.orbix.engine.modules.giftcard.domain.entity.GiftCardTxn;
import com.orbix.engine.modules.giftcard.domain.enums.GiftCardTxnKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GiftCardTxnRepository extends JpaRepository<GiftCardTxn, Long> {

    List<GiftCardTxn> findByGiftCardIdOrderByOccurredAtAsc(Long giftCardId);

    /** Idempotency probe — producer checks before inserting. */
    Optional<GiftCardTxn> findByGiftCardIdAndRefDocTypeAndRefDocIdAndKind(
        Long giftCardId, String refDocType, Long refDocId, GiftCardTxnKind kind);
}

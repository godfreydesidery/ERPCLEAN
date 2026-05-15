package com.orbix.engine.modules.giftcard.domain.entity;

import com.orbix.engine.modules.giftcard.domain.enums.GiftCardTxnKind;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only row in the gift-card balance ledger. DATA-MODEL §17.7.
 * Uniqueness on {@code (gift_card_id, ref_doc_type, ref_doc_id, kind)} is
 * the idempotency key — a replayed redeem call from a POS retry collides
 * on the same triple and the constraint kicks in.
 */
@Entity
@Table(name = "gift_card_txn",
    uniqueConstraints = @UniqueConstraint(name = "uk_gift_card_txn_ref",
        columnNames = {"gift_card_id", "ref_doc_type", "ref_doc_id", "kind"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class GiftCardTxn {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gift_card_txn_seq")
    @SequenceGenerator(name = "gift_card_txn_seq", sequenceName = "gift_card_txn_seq",
        allocationSize = 50)
    @Setter
    private Long id;

    @Column(name = "gift_card_id", nullable = false)
    private Long giftCardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GiftCardTxnKind kind;

    /** Always positive; the kind carries the sign. */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    /** Post-tx snapshot of {@code gift_card.current_balance}. */
    @Column(name = "balance_after", nullable = false, precision = 18, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "ref_doc_type", length = 40)
    private String refDocType;

    @Column(name = "ref_doc_id")
    private Long refDocId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "by_user_id", nullable = false)
    private Long byUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @SuppressWarnings("java:S107")
    public GiftCardTxn(Long giftCardId, GiftCardTxnKind kind, BigDecimal amount,
                       BigDecimal balanceAfter, String refDocType, Long refDocId,
                       Instant occurredAt, Long byUserId) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("gift_card_txn amount must be > 0");
        }
        this.giftCardId = giftCardId;
        this.kind = kind;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.refDocType = refDocType;
        this.refDocId = refDocId;
        this.occurredAt = occurredAt;
        this.byUserId = byUserId;
        this.createdAt = Instant.now();
    }
}

package com.orbix.engine.modules.giftcard.domain.entity;

import com.orbix.engine.modules.giftcard.domain.enums.GiftCardStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Bearer stored-value voucher. DATA-MODEL §17.6. Balance is a liability,
 * tracked separately from {@code cash_book}; the cash-side of issuance is
 * a regular {@code cash_entry} but redemption is a liability transfer
 * (no cash entry).
 *
 * <p>{@code code} is a bearer secret — {@link #toString} excludes it; log
 * formatters should redact to the last 4 digits.
 */
@Entity
@Table(name = "gift_card",
    uniqueConstraints = @UniqueConstraint(name = "uk_gift_card_code", columnNames = "code"))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
@ToString(exclude = "code")
public class GiftCard {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gift_card_seq")
    @SequenceGenerator(name = "gift_card_seq", sequenceName = "gift_card_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "issued_by_branch_id", nullable = false)
    private Long issuedByBranchId;

    @Column(name = "issued_by_user_id", nullable = false)
    private Long issuedByUserId;

    @Column(name = "initial_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal initialValue;

    @Column(name = "current_balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal currentBalance;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GiftCardStatus status = GiftCardStatus.ACTIVE;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @SuppressWarnings("java:S107")
    public GiftCard(String code, Long companyId, Long issuedByBranchId, Long issuedByUserId,
                    BigDecimal initialValue, String currencyCode, Instant expiresAt, Long actorId) {
        if (initialValue == null || initialValue.signum() <= 0) {
            throw new IllegalArgumentException("gift_card initial_value must be > 0");
        }
        this.code = code;
        this.companyId = companyId;
        this.issuedByBranchId = issuedByBranchId;
        this.issuedByUserId = issuedByUserId;
        this.initialValue = initialValue;
        this.currentBalance = initialValue;
        this.currencyCode = currencyCode;
        this.status = GiftCardStatus.ACTIVE;
        Instant now = Instant.now();
        this.issuedAt = now;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    /** Debit on redemption. Caller (service) wraps the txn write + status flip. */
    public void debit(BigDecimal amount, Long actorId) {
        requireRedeemable();
        if (amount.compareTo(currentBalance) > 0) {
            throw new IllegalArgumentException(
                "Redeem amount " + amount + " exceeds card balance " + currentBalance);
        }
        this.currentBalance = this.currentBalance.subtract(amount);
        if (this.currentBalance.signum() == 0) {
            this.status = GiftCardStatus.FULLY_REDEEMED;
        }
        touch(actorId);
    }

    /** Credit on refund. Re-activates a FULLY_REDEEMED card. */
    public void credit(BigDecimal amount, Long actorId) {
        if (status == GiftCardStatus.FROZEN || status == GiftCardStatus.EXPIRED) {
            throw new IllegalStateException(
                "Cannot refund-credit a card that is " + status);
        }
        this.currentBalance = this.currentBalance.add(amount);
        if (status == GiftCardStatus.FULLY_REDEEMED) {
            this.status = GiftCardStatus.ACTIVE;
        }
        touch(actorId);
    }

    public void freeze(Long actorId) {
        if (status == GiftCardStatus.EXPIRED || status == GiftCardStatus.REFUNDED) {
            throw new IllegalStateException("Cannot freeze a card that is " + status);
        }
        this.status = GiftCardStatus.FROZEN;
        touch(actorId);
    }

    public void unfreeze(Long actorId) {
        if (status != GiftCardStatus.FROZEN) {
            throw new IllegalStateException("Only FROZEN cards can be unfrozen (was " + status + ")");
        }
        this.status = currentBalance.signum() > 0 ? GiftCardStatus.ACTIVE : GiftCardStatus.FULLY_REDEEMED;
        touch(actorId);
    }

    /** Auto-expire (scheduled job). Zeroes the balance — caller writes the EXPIRE txn. */
    public BigDecimal expire(Long actorId) {
        if (status != GiftCardStatus.ACTIVE && status != GiftCardStatus.FULLY_REDEEMED) {
            throw new IllegalStateException("Only ACTIVE / FULLY_REDEEMED cards can be expired (was " + status + ")");
        }
        BigDecimal forfeited = this.currentBalance;
        this.currentBalance = BigDecimal.ZERO;
        this.status = GiftCardStatus.EXPIRED;
        touch(actorId);
        return forfeited;
    }

    private void requireRedeemable() {
        if (status != GiftCardStatus.ACTIVE) {
            throw new IllegalStateException("Gift card is " + status + " — cannot redeem");
        }
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw new IllegalStateException("Gift card expired at " + expiresAt);
        }
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}

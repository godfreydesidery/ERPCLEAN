package com.orbix.engine.modules.orders.domain.entity;

import com.orbix.engine.modules.orders.domain.enums.OrderPaymentDirection;
import com.orbix.engine.modules.orders.domain.enums.OrderPaymentMethod;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only deposit / instalment / final / refund row against a customer
 * order. DATA-MODEL §17.10.
 *
 * <p>For {@link OrderPaymentDirection#IN} the cash side flows via
 * {@link com.orbix.engine.modules.cash.service.CashLedgerService} (CASH /
 * BANK_TRANSFER / MOBILE_MONEY / CHEQUE) or via
 * {@link com.orbix.engine.modules.giftcard.service.GiftCardService#redeem}
 * (GIFT_CARD method). For {@link OrderPaymentDirection#OUT} (cancel refund)
 * the flip side is mirrored — cash OUT or {@code refundCredit} respectively.
 * CARD never moves physical cash and has no cash-side row.
 */
@Entity
@Table(name = "customer_order_payment",
    uniqueConstraints = @UniqueConstraint(name = "uk_customer_order_payment_idempotency",
        columnNames = {"customer_order_id", "idempotency_key"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class CustomerOrderPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_order_payment_seq")
    @SequenceGenerator(name = "customer_order_payment_seq",
        sequenceName = "customer_order_payment_seq", allocationSize = 50)
    private Long id;

    @Column(name = "customer_order_id", nullable = false)
    private Long customerOrderId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderPaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private OrderPaymentDirection direction;

    /** External reference — gift-card code (masked), bank txn ref, card last-4, etc. */
    @Column(length = 80)
    private String reference;

    @Column(length = 200)
    private String notes;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "by_user_id", nullable = false)
    private Long byUserId;

    /** FK into {@code cash_entry} when the method flowed cash. Null for CARD + GIFT_CARD. */
    @Column(name = "ref_cash_entry_id")
    private Long refCashEntryId;

    /** FK into {@code gift_card_txn} when method = GIFT_CARD. Null otherwise. */
    @Column(name = "ref_giftcard_txn_id")
    private Long refGiftcardTxnId;

    /** Client-supplied idempotency key — UNIQUE per (order, key) blocks accidental double-post. */
    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @SuppressWarnings("java:S107")
    public CustomerOrderPayment(Long customerOrderId, BigDecimal amount,
                                OrderPaymentMethod method, OrderPaymentDirection direction,
                                String reference, String notes, Instant occurredAt,
                                Long byUserId, String idempotencyKey) {
        this.customerOrderId = customerOrderId;
        this.amount = amount;
        this.method = method;
        this.direction = direction;
        this.reference = reference;
        this.notes = notes;
        this.occurredAt = occurredAt;
        this.byUserId = byUserId;
        this.idempotencyKey = idempotencyKey;
    }
}

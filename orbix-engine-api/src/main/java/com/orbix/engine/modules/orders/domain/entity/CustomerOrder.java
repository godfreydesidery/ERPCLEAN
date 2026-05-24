package com.orbix.engine.modules.orders.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderStatus;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Layby / pre-order header. DATA-MODEL §17.8.
 *
 * <p>Distinct from {@code sales_invoice} — ownership doesn't transfer until
 * {@code COLLECTED}; until then the customer holds an option (with deposit)
 * and we hold the stock (for LAYBY) or schedule production (for PRE_ORDER).
 * Settlement is bookkept on the {@code customer_order_payment} child rows;
 * cash side flows via {@link com.orbix.engine.modules.cash.service.CashLedgerService}
 * for non-gift-card methods.
 */
@Entity
@Table(name = "customer_order",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_order_uid", columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_customer_order_branch_number",
            columnNames = {"branch_id", "number"})
    })
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class CustomerOrder extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_order_seq")
    @SequenceGenerator(name = "customer_order_seq", sequenceName = "customer_order_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    /** Required for production-tied pre-orders (the bakery / deli that will produce). */
    @Column(name = "section_id")
    private Long sectionId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerOrderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CustomerOrderStatus status = CustomerOrderStatus.DRAFT;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "reserved_until")
    private Instant reservedUntil;

    @Column(name = "deposit_required_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal depositRequiredAmount = BigDecimal.ZERO;

    @Column(name = "deposit_paid_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal depositPaidAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "balance_due", nullable = false, precision = 18, scale = 4)
    private BigDecimal balanceDue = BigDecimal.ZERO;

    /** Sum of OUT payments — refund-on-cancel within the policy window. */
    @Column(name = "refunded_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    /** Deposit retained on cancel-past-window / expire. */
    @Column(name = "forfeited_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal forfeitedAmount = BigDecimal.ZERO;

    @Column(name = "reserved_at")
    private Instant reservedAt;

    @Column(name = "reserved_by")
    private Long reservedBy;

    @Column(name = "collected_at")
    private Instant collectedAt;

    @Column(name = "collected_by")
    private Long collectedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(length = 2000)
    private String notes;

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
    public CustomerOrder(String number, Long companyId, Long branchId, Long sectionId,
                         Long customerId, CustomerOrderType type, String currencyCode,
                         Instant reservedUntil, BigDecimal depositRequiredAmount,
                         String notes, Long actorId) {
        this.number = number;
        this.companyId = companyId;
        this.branchId = branchId;
        this.sectionId = sectionId;
        this.customerId = customerId;
        this.type = type;
        this.currencyCode = currencyCode;
        this.reservedUntil = reservedUntil;
        this.depositRequiredAmount = depositRequiredAmount != null ? depositRequiredAmount : BigDecimal.ZERO;
        this.status = CustomerOrderStatus.DRAFT;
        this.notes = notes;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void rollUpTotal(BigDecimal total) {
        this.totalAmount = total != null ? total : BigDecimal.ZERO;
        this.balanceDue = this.totalAmount.subtract(this.paidAmount);
    }

    public void markReserved(Long actorId) {
        if (status != CustomerOrderStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT orders can be reserved (was " + status + ")");
        }
        this.status = CustomerOrderStatus.RESERVED;
        this.reservedAt = Instant.now();
        this.reservedBy = actorId;
        touch(actorId);
    }

    /**
     * Applies an inbound payment of {@code amount}. Caller passes the running
     * deposit total (count toward the deposit requirement). Caller has already
     * ensured the order is in an open state.
     */
    public void applyPayment(BigDecimal amount, boolean countsTowardDeposit, Long actorId) {
        if (!status.isOpen() || status == CustomerOrderStatus.READY
                || status == CustomerOrderStatus.COLLECTED) {
            throw new IllegalStateException(
                "Cannot accept further payments while order is in status " + status);
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive: " + amount);
        }
        BigDecimal newPaid = this.paidAmount.add(amount);
        if (newPaid.compareTo(this.totalAmount) > 0) {
            throw new IllegalArgumentException(
                "Payment " + amount + " would over-pay order " + number
                    + " (paid would be " + newPaid + " of total " + totalAmount + ")");
        }
        this.paidAmount = newPaid;
        this.balanceDue = this.totalAmount.subtract(this.paidAmount);
        if (countsTowardDeposit) {
            this.depositPaidAmount = this.depositPaidAmount.add(amount);
        }
        advanceAfterPayment();
        touch(actorId);
    }

    private void advanceAfterPayment() {
        if (balanceDue.signum() == 0) {
            // Fully paid. LAYBY moves straight to READY (ready for collection);
            // PRE_ORDER stays in PARTIALLY_PAID / DEPOSIT_PAID until production
            // posts output — production module flips status to READY then.
            if (type == CustomerOrderType.LAYBY) {
                this.status = CustomerOrderStatus.READY;
            } else {
                this.status = depositPaidAmount.compareTo(depositRequiredAmount) >= 0
                    ? CustomerOrderStatus.DEPOSIT_PAID
                    : CustomerOrderStatus.PARTIALLY_PAID;
            }
            return;
        }
        if (depositPaidAmount.compareTo(depositRequiredAmount) >= 0
                && paidAmount.compareTo(depositPaidAmount) > 0) {
            this.status = CustomerOrderStatus.PARTIALLY_PAID;
        } else if (depositPaidAmount.compareTo(depositRequiredAmount) >= 0) {
            this.status = CustomerOrderStatus.DEPOSIT_PAID;
        } else {
            // Status remains as it was (DRAFT before first payment / RESERVED after).
            // Don't promote until deposit covered.
            this.status = (status == CustomerOrderStatus.DRAFT) ? CustomerOrderStatus.DRAFT : status;
        }
    }

    /** Bookkeeping for a refund on cancel — the caller has already posted the cash / gift-card OUT. */
    public void recordRefund(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        this.refundedAmount = this.refundedAmount.add(amount);
    }

    public void markReady(Long actorId) {
        if (status != CustomerOrderStatus.DEPOSIT_PAID
                && status != CustomerOrderStatus.PARTIALLY_PAID) {
            throw new IllegalStateException("Only DEPOSIT_PAID / PARTIALLY_PAID orders can be marked READY (was "
                + status + ")");
        }
        this.status = CustomerOrderStatus.READY;
        touch(actorId);
    }

    public void markCollected(Long actorId) {
        if (status != CustomerOrderStatus.READY) {
            throw new IllegalStateException("Only READY orders can be collected (was " + status + ")");
        }
        if (balanceDue.signum() != 0) {
            throw new IllegalStateException(
                "Cannot collect order with outstanding balance " + balanceDue);
        }
        this.status = CustomerOrderStatus.COLLECTED;
        this.collectedAt = Instant.now();
        this.collectedBy = actorId;
        touch(actorId);
    }

    public void cancel(String reason, BigDecimal forfeited, Long actorId) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Cannot cancel an order in terminal status " + status);
        }
        this.status = CustomerOrderStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = Instant.now();
        this.cancelledBy = actorId;
        if (forfeited != null && forfeited.signum() > 0) {
            this.forfeitedAmount = this.forfeitedAmount.add(forfeited);
        }
        touch(actorId);
    }

    public void expire(BigDecimal forfeited, Long actorId) {
        if (status != CustomerOrderStatus.RESERVED
                && status != CustomerOrderStatus.DEPOSIT_PAID
                && status != CustomerOrderStatus.PARTIALLY_PAID) {
            throw new IllegalStateException("Cannot expire an order in status " + status);
        }
        this.status = CustomerOrderStatus.EXPIRED;
        this.expiredAt = Instant.now();
        if (forfeited != null && forfeited.signum() > 0) {
            this.forfeitedAmount = this.forfeitedAmount.add(forfeited);
        }
        touch(actorId);
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}

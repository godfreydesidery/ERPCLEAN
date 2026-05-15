package com.orbix.engine.modules.cash.domain.entity;

import com.orbix.engine.modules.cash.domain.enums.PaymentMethod;
import com.orbix.engine.modules.cash.domain.enums.SupplierPaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Supplier payment — header. DATA-MODEL.md §10.4. */
@Entity
@Table(name = "supplier_payment",
    uniqueConstraints = @UniqueConstraint(name = "uk_supplier_payment_branch_number",
        columnNames = {"branch_id", "number"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class SupplierPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "supplier_payment_seq")
    @SequenceGenerator(name = "supplier_payment_seq", sequenceName = "supplier_payment_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(length = 80)
    private String reference;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "allocated_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal allocatedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SupplierPaymentStatus status = SupplierPaymentStatus.DRAFT;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by")
    private Long postedBy;

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

    @SuppressWarnings("java:S107")  // payment header is inherently wide
    public SupplierPayment(String number, Long companyId, Long branchId, Long supplierId,
                           LocalDate paymentDate, PaymentMethod method, String reference,
                           String currencyCode, BigDecimal totalAmount, String notes, Long actorId) {
        this.number = number;
        this.companyId = companyId;
        this.branchId = branchId;
        this.supplierId = supplierId;
        this.paymentDate = paymentDate;
        this.method = method;
        this.reference = reference;
        this.currencyCode = currencyCode;
        this.totalAmount = totalAmount;
        this.allocatedAmount = BigDecimal.ZERO;
        this.status = SupplierPaymentStatus.DRAFT;
        this.notes = notes;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void setAllocatedAmount(BigDecimal allocated) {
        this.allocatedAmount = allocated;
    }

    public void post(Long actorId) {
        requireStatus(SupplierPaymentStatus.DRAFT);
        this.status = SupplierPaymentStatus.POSTED;
        this.postedAt = Instant.now();
        this.postedBy = actorId;
        touch(actorId);
    }

    public void cancel(Long actorId) {
        requireStatus(SupplierPaymentStatus.DRAFT);
        this.status = SupplierPaymentStatus.CANCELLED;
        touch(actorId);
    }

    private void requireStatus(SupplierPaymentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Supplier payment is " + status + ", expected " + expected);
        }
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}

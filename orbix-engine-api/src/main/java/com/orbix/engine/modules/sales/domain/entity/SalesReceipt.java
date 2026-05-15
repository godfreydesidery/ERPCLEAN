package com.orbix.engine.modules.sales.domain.entity;

import com.orbix.engine.modules.sales.domain.enums.ReceiptMethod;
import com.orbix.engine.modules.sales.domain.enums.SalesReceiptStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Sales receipt — header. DATA-MODEL.md §6.5. */
@Entity
@Table(name = "sales_receipt",
    uniqueConstraints = @UniqueConstraint(name = "uk_sales_receipt_branch_number",
        columnNames = {"branch_id", "number"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class SalesReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sales_receipt_seq")
    @SequenceGenerator(name = "sales_receipt_seq", sequenceName = "sales_receipt_seq",
        allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceiptMethod method;

    @Column(length = 80)
    private String reference;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "allocated_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal allocatedAmount = BigDecimal.ZERO;

    @Column(name = "unallocated_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal unallocatedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SalesReceiptStatus status = SalesReceiptStatus.DRAFT;

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

    @SuppressWarnings("java:S107")
    public SalesReceipt(String number, Long companyId, Long branchId, Long customerId,
                        LocalDate receiptDate, ReceiptMethod method, String reference,
                        String currencyCode, BigDecimal totalAmount, String notes, Long actorId) {
        this.number = number;
        this.companyId = companyId;
        this.branchId = branchId;
        this.customerId = customerId;
        this.receiptDate = receiptDate;
        this.method = method;
        this.reference = reference;
        this.currencyCode = currencyCode;
        this.totalAmount = totalAmount;
        this.allocatedAmount = BigDecimal.ZERO;
        this.unallocatedAmount = totalAmount;
        this.status = SalesReceiptStatus.DRAFT;
        this.notes = notes;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void setAllocated(BigDecimal allocated) {
        this.allocatedAmount = allocated;
        this.unallocatedAmount = totalAmount.subtract(allocated);
    }

    public void post(Long actorId) {
        requireStatus(SalesReceiptStatus.DRAFT);
        this.status = SalesReceiptStatus.POSTED;
        this.postedAt = Instant.now();
        this.postedBy = actorId;
        touch(actorId);
    }

    public void cancel(Long actorId) {
        requireStatus(SalesReceiptStatus.DRAFT);
        this.status = SalesReceiptStatus.CANCELLED;
        touch(actorId);
    }

    private void requireStatus(SalesReceiptStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Sales receipt is " + status + ", expected " + expected);
        }
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}

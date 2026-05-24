package com.orbix.engine.modules.sales.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.sales.domain.enums.CustomerReturnStatus;
import com.orbix.engine.modules.sales.domain.enums.ReturnReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Customer return — header. DATA-MODEL.md §6.7. */
@Entity
@Table(name = "customer_return",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_return_uid", columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_customer_return_branch_number",
            columnNames = {"branch_id", "number"})
    })
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class CustomerReturn extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_return_seq")
    @SequenceGenerator(name = "customer_return_seq", sequenceName = "customer_return_seq",
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

    @Column(name = "original_invoice_id")
    private Long originalInvoiceId;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnReason reason;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CustomerReturnStatus status = CustomerReturnStatus.DRAFT;

    @Column(nullable = false)
    private boolean restock = true;

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
    public CustomerReturn(String number, Long companyId, Long branchId, Long customerId,
                          Long originalInvoiceId, LocalDate returnDate, ReturnReason reason,
                          boolean restock, String notes, Long actorId) {
        this.number = number;
        this.companyId = companyId;
        this.branchId = branchId;
        this.customerId = customerId;
        this.originalInvoiceId = originalInvoiceId;
        this.returnDate = returnDate;
        this.reason = reason;
        this.restock = restock;
        this.notes = notes;
        this.status = CustomerReturnStatus.DRAFT;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void rollUpTotal(BigDecimal total) {
        this.totalAmount = total;
    }

    public void post(Long actorId) {
        requireStatus(CustomerReturnStatus.DRAFT);
        this.status = CustomerReturnStatus.POSTED;
        this.postedAt = Instant.now();
        this.postedBy = actorId;
        touch(actorId);
    }

    public void markCredited(Long actorId) {
        requireStatus(CustomerReturnStatus.POSTED);
        this.status = CustomerReturnStatus.CREDITED;
        touch(actorId);
    }

    public void cancel(Long actorId) {
        requireStatus(CustomerReturnStatus.DRAFT);
        this.status = CustomerReturnStatus.CANCELLED;
        touch(actorId);
    }

    private void requireStatus(CustomerReturnStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Customer return is " + status + ", expected " + expected);
        }
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}

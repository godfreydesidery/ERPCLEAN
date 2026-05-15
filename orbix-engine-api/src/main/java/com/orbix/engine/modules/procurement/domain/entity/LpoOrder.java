package com.orbix.engine.modules.procurement.domain.entity;

import com.orbix.engine.modules.procurement.domain.enums.LpoOrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Local Purchase Order — header. DATA-MODEL.md §5.3. */
@Entity
@Table(name = "lpo_order",
    uniqueConstraints = @UniqueConstraint(name = "uk_lpo_order_branch_number",
        columnNames = {"branch_id", "number"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class LpoOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lpo_order_seq")
    @SequenceGenerator(name = "lpo_order_seq", sequenceName = "lpo_order_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "subtotal_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LpoOrderStatus status = LpoOrderStatus.DRAFT;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

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

    @SuppressWarnings("java:S107")  // LPO header is inherently wide; a VO would only shuffle args
    public LpoOrder(String number, Long companyId, Long branchId, Long supplierId,
                    LocalDate orderDate, LocalDate expectedDeliveryDate,
                    String currencyCode, String notes, Long actorId) {
        this.number = number;
        this.companyId = companyId;
        this.branchId = branchId;
        this.supplierId = supplierId;
        this.orderDate = orderDate;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.currencyCode = currencyCode;
        this.notes = notes;
        this.status = LpoOrderStatus.DRAFT;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    /** Re-derive the header subtotal/tax/total from line totals + tax. */
    public void rollUpTotals(BigDecimal subtotal, BigDecimal tax) {
        this.subtotalAmount = subtotal;
        this.taxAmount = tax;
        this.totalAmount = subtotal.add(tax);
    }

    public void editHeader(Long supplierId, LocalDate orderDate, LocalDate expectedDeliveryDate,
                           String currencyCode, String notes, Long actorId) {
        requireStatus(LpoOrderStatus.DRAFT);
        this.supplierId = supplierId;
        this.orderDate = orderDate;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.currencyCode = currencyCode;
        this.notes = notes;
        touch(actorId);
    }

    public void submit(Long actorId) {
        requireStatus(LpoOrderStatus.DRAFT);
        this.status = LpoOrderStatus.PENDING_APPROVAL;
        touch(actorId);
    }

    public void approve(Long actorId) {
        if (status != LpoOrderStatus.DRAFT && status != LpoOrderStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only DRAFT or PENDING_APPROVAL can be approved (was " + status + ")");
        }
        this.status = LpoOrderStatus.APPROVED;
        this.approvedBy = actorId;
        this.approvedAt = Instant.now();
        touch(actorId);
    }

    public void cancel(Long actorId) {
        if (status != LpoOrderStatus.DRAFT && status != LpoOrderStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only DRAFT or PENDING_APPROVAL can be cancelled (was " + status + ")");
        }
        this.status = LpoOrderStatus.CANCELLED;
        touch(actorId);
    }

    /**
     * Called by GRN posting. {@code fullyReceived} = every line has
     * received_qty == ordered_qty. Allowed transitions:
     * APPROVED / PARTIALLY_RECEIVED → PARTIALLY_RECEIVED / RECEIVED.
     */
    public void markReceiveProgress(boolean fullyReceived, Long actorId) {
        if (status != LpoOrderStatus.APPROVED && status != LpoOrderStatus.PARTIALLY_RECEIVED) {
            throw new IllegalStateException(
                "Cannot record receipt on an LPO in status " + status);
        }
        this.status = fullyReceived ? LpoOrderStatus.RECEIVED : LpoOrderStatus.PARTIALLY_RECEIVED;
        touch(actorId);
    }

    private void requireStatus(LpoOrderStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("LPO is " + status + ", expected " + expected);
        }
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}

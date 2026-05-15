package com.orbix.engine.modules.procurement.domain.entity;

import com.orbix.engine.modules.procurement.domain.enums.GrnStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Goods Received Note — header. DATA-MODEL.md §5.5. */
@Entity
@Table(name = "grn",
    uniqueConstraints = @UniqueConstraint(name = "uk_grn_branch_number",
        columnNames = {"branch_id", "number"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class Grn {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "grn_seq")
    @SequenceGenerator(name = "grn_seq", sequenceName = "grn_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    /** Null = direct GRN (caller must hold {@code GRN.DIRECT}). */
    @Column(name = "lpo_order_id")
    private Long lpoOrderId;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "supplier_delivery_note", length = 80)
    private String supplierDeliveryNote;

    @Column(name = "subtotal_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GrnStatus status = GrnStatus.DRAFT;

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

    @SuppressWarnings("java:S107")  // GRN header is inherently wide
    public Grn(String number, Long companyId, Long branchId, Long supplierId, Long lpoOrderId,
               LocalDate receivedDate, String supplierDeliveryNote, String notes, Long actorId) {
        this.number = number;
        this.companyId = companyId;
        this.branchId = branchId;
        this.supplierId = supplierId;
        this.lpoOrderId = lpoOrderId;
        this.receivedDate = receivedDate;
        this.supplierDeliveryNote = supplierDeliveryNote;
        this.notes = notes;
        this.status = GrnStatus.DRAFT;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void rollUpTotals(BigDecimal subtotal, BigDecimal tax) {
        this.subtotalAmount = subtotal;
        this.taxAmount = tax;
        this.totalAmount = subtotal.add(tax);
    }

    public void post(Long actorId) {
        requireStatus(GrnStatus.DRAFT);
        this.status = GrnStatus.POSTED;
        this.postedAt = Instant.now();
        this.postedBy = actorId;
        touch(actorId);
    }

    public void cancel(Long actorId) {
        requireStatus(GrnStatus.DRAFT);
        this.status = GrnStatus.CANCELLED;
        touch(actorId);
    }

    private void requireStatus(GrnStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("GRN is " + status + ", expected " + expected);
        }
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}

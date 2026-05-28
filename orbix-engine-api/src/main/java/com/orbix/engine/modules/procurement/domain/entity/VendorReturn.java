package com.orbix.engine.modules.procurement.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.procurement.domain.enums.VendorReturnReason;
import com.orbix.engine.modules.procurement.domain.enums.VendorReturnStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Vendor return — header. DATA-MODEL.md §5.x (Slice H.1). */
@Entity
@Table(name = "vendor_return",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_vendor_return_uid", columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_vendor_return_branch_number",
            columnNames = {"branch_id", "number"})
    })
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class VendorReturn extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vendor_return_seq")
    @SequenceGenerator(name = "vendor_return_seq", sequenceName = "vendor_return_seq",
        allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "original_grn_id")
    private Long originalGrnId;

    @Column(name = "original_supplier_invoice_id")
    private Long originalSupplierInvoiceId;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VendorReturnReason reason;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VendorReturnStatus status = VendorReturnStatus.DRAFT;

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
    public VendorReturn(String number, Long companyId, Long branchId, Long supplierId,
                        Long originalGrnId, Long originalSupplierInvoiceId,
                        LocalDate returnDate, VendorReturnReason reason,
                        boolean restock, String notes, Long actorId) {
        this.number = number;
        this.companyId = companyId;
        this.branchId = branchId;
        this.supplierId = supplierId;
        this.originalGrnId = originalGrnId;
        this.originalSupplierInvoiceId = originalSupplierInvoiceId;
        this.returnDate = returnDate;
        this.reason = reason;
        this.restock = restock;
        this.notes = notes;
        this.status = VendorReturnStatus.DRAFT;
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
        requireStatus(VendorReturnStatus.DRAFT);
        this.status = VendorReturnStatus.POSTED;
        this.postedAt = Instant.now();
        this.postedBy = actorId;
        touch(actorId);
    }

    public void markCredited(Long actorId) {
        requireStatus(VendorReturnStatus.POSTED);
        this.status = VendorReturnStatus.CREDITED;
        touch(actorId);
    }

    public void cancel(Long actorId) {
        requireStatus(VendorReturnStatus.DRAFT);
        this.status = VendorReturnStatus.CANCELLED;
        touch(actorId);
    }

    private void requireStatus(VendorReturnStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Vendor return is " + status + ", expected " + expected);
        }
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}

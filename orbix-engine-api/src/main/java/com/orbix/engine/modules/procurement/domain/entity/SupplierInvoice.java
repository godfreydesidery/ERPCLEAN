package com.orbix.engine.modules.procurement.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Supplier invoice — header. DATA-MODEL.md §5.7. */
@Entity
@Table(name = "supplier_invoice",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_supplier_invoice_uid",
            columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_supplier_invoice_branch_number",
            columnNames = {"branch_id", "number"}),
        @UniqueConstraint(name = "uk_supplier_invoice_supplier_no",
            columnNames = {"supplier_id", "supplier_invoice_no"})
    })
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class SupplierInvoice extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "supplier_invoice_seq")
    @SequenceGenerator(name = "supplier_invoice_seq", sequenceName = "supplier_invoice_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "supplier_invoice_no", nullable = false, length = 80)
    private String supplierInvoiceNo;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "subtotal_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SupplierInvoiceStatus status = SupplierInvoiceStatus.DRAFT;

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

    @SuppressWarnings("java:S107")  // invoice header is inherently wide
    public SupplierInvoice(String number, String supplierInvoiceNo, Long companyId, Long branchId,
                           Long supplierId, LocalDate invoiceDate, LocalDate dueDate,
                           String currencyCode, BigDecimal subtotal, BigDecimal tax,
                           String notes, Long actorId) {
        this.number = number;
        this.supplierInvoiceNo = supplierInvoiceNo;
        this.companyId = companyId;
        this.branchId = branchId;
        this.supplierId = supplierId;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.currencyCode = currencyCode;
        this.subtotalAmount = subtotal;
        this.taxAmount = tax;
        this.totalAmount = subtotal.add(tax);
        this.status = SupplierInvoiceStatus.DRAFT;
        this.notes = notes;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void post(Long actorId) {
        requireStatus(SupplierInvoiceStatus.DRAFT);
        this.status = SupplierInvoiceStatus.POSTED;
        this.postedAt = Instant.now();
        this.postedBy = actorId;
        touch(actorId);
    }

    public void cancel(Long actorId) {
        if (status == SupplierInvoiceStatus.PARTIALLY_PAID
                || status == SupplierInvoiceStatus.PAID
                || status == SupplierInvoiceStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel an invoice in status " + status);
        }
        this.status = SupplierInvoiceStatus.CANCELLED;
        touch(actorId);
    }

    /** Outstanding amount: {@code total - paid}. Never negative under normal flow. */
    public BigDecimal outstandingAmount() {
        return totalAmount.subtract(paidAmount);
    }

    /**
     * Advances {@code paid_amount} by the (positive) allocation amount and
     * flips the status: paid &lt; total → PARTIALLY_PAID; paid == total → PAID.
     * Only callable on POSTED or PARTIALLY_PAID invoices.
     */
    public void applyPayment(BigDecimal amount, Long actorId) {
        if (status != SupplierInvoiceStatus.POSTED
                && status != SupplierInvoiceStatus.PARTIALLY_PAID) {
            throw new IllegalStateException(
                "Cannot apply a payment to an invoice in status " + status);
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive: " + amount);
        }
        BigDecimal newPaid = paidAmount.add(amount);
        if (newPaid.compareTo(totalAmount) > 0) {
            throw new IllegalArgumentException(
                "Allocation " + amount + " would over-pay invoice " + number
                    + " (paid would be " + newPaid + " of total " + totalAmount + ")");
        }
        this.paidAmount = newPaid;
        this.status = paidAmount.compareTo(totalAmount) == 0
            ? SupplierInvoiceStatus.PAID
            : SupplierInvoiceStatus.PARTIALLY_PAID;
        touch(actorId);
    }

    private void requireStatus(SupplierInvoiceStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Supplier invoice is " + status + ", expected " + expected);
        }
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}

package com.orbix.engine.modules.sales.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.sales.domain.enums.PaymentTerms;
import com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Sales invoice — header. DATA-MODEL.md §6.3. */
@Entity
@Table(name = "sales_invoice",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sales_invoice_uid", columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_sales_invoice_branch_number",
            columnNames = {"branch_id", "number"})
    })
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class SalesInvoice extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sales_invoice_seq")
    @SequenceGenerator(name = "sales_invoice_seq", sequenceName = "sales_invoice_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "sales_agent_id")
    private Long salesAgentId;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_terms", nullable = false, length = 20)
    private PaymentTerms paymentTerms;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "price_list_id", nullable = false)
    private Long priceListId;

    @Column(name = "subtotal_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SalesInvoiceStatus status = SalesInvoiceStatus.DRAFT;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by")
    private Long postedBy;

    /** The business date the invoice was posted under — drives the same-day-only void rule. */
    @Column(name = "posted_business_date")
    private LocalDate postedBusinessDate;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "void_reason", length = 200)
    private String voidReason;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    /** Slice C — credit-limit override stamped at POST time when caller holds {@code SALES_INVOICE.OVERRIDE_CREDIT}. */
    @Column(name = "credit_override", nullable = false)
    private boolean creditOverride = false;

    @Column(name = "credit_override_by")
    private Long creditOverrideBy;

    @Column(name = "credit_override_reason", length = 500)
    private String creditOverrideReason;

    /** Slice C — monotonically incremented per {@code POST .../uid/{uid}/reprint}. */
    @Column(name = "reprint_count", nullable = false)
    private int reprintCount = 0;

    @Column(length = 80)
    private String reference;

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

    @SuppressWarnings("java:S107")  // sales invoice header is inherently wide
    public SalesInvoice(String number, Long companyId, Long branchId, Long customerId,
                        Long salesAgentId, LocalDate invoiceDate, LocalDate dueDate,
                        PaymentTerms paymentTerms, String currencyCode, Long priceListId,
                        String reference, String notes, Long actorId) {
        this.number = number;
        this.companyId = companyId;
        this.branchId = branchId;
        this.customerId = customerId;
        this.salesAgentId = salesAgentId;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.paymentTerms = paymentTerms;
        this.currencyCode = currencyCode;
        this.priceListId = priceListId;
        this.reference = reference;
        this.notes = notes;
        this.status = SalesInvoiceStatus.DRAFT;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void rollUpTotals(BigDecimal subtotal, BigDecimal headerDiscount, BigDecimal tax) {
        this.subtotalAmount = subtotal;
        this.discountAmount = headerDiscount;
        this.taxAmount = tax;
        this.totalAmount = subtotal.subtract(headerDiscount).add(tax);
    }

    public void post(LocalDate businessDate, Long actorId) {
        requireStatus(SalesInvoiceStatus.DRAFT);
        this.status = SalesInvoiceStatus.POSTED;
        this.postedAt = Instant.now();
        this.postedBy = actorId;
        this.postedBusinessDate = businessDate;
        touch(actorId);
    }

    /** Slice C — record that this post exercised the credit-limit override. */
    public void markCreditOverride(Long actorId, String reason) {
        this.creditOverride = true;
        this.creditOverrideBy = actorId;
        this.creditOverrideReason = reason;
    }

    /** Slice C — increment the reprint counter; pure audit, no status change. */
    public int recordReprint(Long actorId) {
        if (status != SalesInvoiceStatus.POSTED
                && status != SalesInvoiceStatus.PARTIALLY_PAID
                && status != SalesInvoiceStatus.PAID
                && status != SalesInvoiceStatus.VOIDED) {
            throw new IllegalStateException(
                "Cannot reprint an invoice in status " + status);
        }
        this.reprintCount = this.reprintCount + 1;
        touch(actorId);
        return this.reprintCount;
    }

    public void cancel(Long actorId) {
        cancel(actorId, null);
    }

    public void cancel(Long actorId, String reason) {
        requireStatus(SalesInvoiceStatus.DRAFT);
        this.status = SalesInvoiceStatus.CANCELLED;
        this.cancellationReason = reason;
        touch(actorId);
    }

    /** Same-business-day void. Caller has already checked the day matches. */
    public void voidInvoice(String reason, Long actorId) {
        if (status != SalesInvoiceStatus.POSTED) {
            throw new IllegalStateException("Only POSTED invoices can be voided (was " + status + ")");
        }
        this.status = SalesInvoiceStatus.VOIDED;
        this.voidedAt = Instant.now();
        this.voidedBy = actorId;
        this.voidReason = reason;
        touch(actorId);
    }

    public BigDecimal outstandingAmount() {
        return totalAmount.subtract(paidAmount);
    }

    /** Mirror of {@code SupplierInvoice.applyPayment} — used by F4.3 sales receipts. */
    public void applyReceipt(BigDecimal amount, Long actorId) {
        if (status != SalesInvoiceStatus.POSTED
                && status != SalesInvoiceStatus.PARTIALLY_PAID) {
            throw new IllegalStateException(
                "Cannot apply a receipt to an invoice in status " + status);
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Receipt amount must be positive: " + amount);
        }
        BigDecimal newPaid = paidAmount.add(amount);
        if (newPaid.compareTo(totalAmount) > 0) {
            throw new IllegalArgumentException(
                "Allocation " + amount + " would over-pay invoice " + number
                    + " (paid would be " + newPaid + " of total " + totalAmount + ")");
        }
        this.paidAmount = newPaid;
        this.status = paidAmount.compareTo(totalAmount) == 0
            ? SalesInvoiceStatus.PAID
            : SalesInvoiceStatus.PARTIALLY_PAID;
        touch(actorId);
    }

    private void requireStatus(SalesInvoiceStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Sales invoice is " + status + ", expected " + expected);
        }
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}

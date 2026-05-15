package com.orbix.engine.modules.sales.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** Links a sales_receipt to one sales_invoice it pays. DATA-MODEL.md §6.6. */
@Entity
@Table(name = "receipt_allocation",
    uniqueConstraints = @UniqueConstraint(name = "uk_receipt_alloc",
        columnNames = {"sales_receipt_id", "sales_invoice_id"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class ReceiptAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "receipt_allocation_seq")
    @SequenceGenerator(name = "receipt_allocation_seq",
        sequenceName = "receipt_allocation_seq", allocationSize = 50)
    private Long id;

    @Column(name = "sales_receipt_id", nullable = false)
    private Long salesReceiptId;

    @Column(name = "sales_invoice_id", nullable = false)
    private Long salesInvoiceId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "allocated_at", nullable = false)
    private Instant allocatedAt;

    @Column(name = "allocated_by", nullable = false)
    private Long allocatedBy;

    public ReceiptAllocation(Long salesReceiptId, Long salesInvoiceId, BigDecimal amount,
                             Long actorId) {
        this.salesReceiptId = salesReceiptId;
        this.salesInvoiceId = salesInvoiceId;
        this.amount = amount;
        this.allocatedAt = Instant.now();
        this.allocatedBy = actorId;
    }
}

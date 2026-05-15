package com.orbix.engine.modules.cash.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One allocation row tying a supplier_payment to one open supplier_invoice. DATA-MODEL.md §10.5. */
@Entity
@Table(name = "supplier_payment_allocation",
    uniqueConstraints = @UniqueConstraint(name = "uk_supplier_payment_alloc",
        columnNames = {"supplier_payment_id", "supplier_invoice_id"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class SupplierPaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "supplier_payment_alloc_seq")
    @SequenceGenerator(name = "supplier_payment_alloc_seq",
        sequenceName = "supplier_payment_alloc_seq", allocationSize = 50)
    private Long id;

    @Column(name = "supplier_payment_id", nullable = false)
    private Long supplierPaymentId;

    @Column(name = "supplier_invoice_id", nullable = false)
    private Long supplierInvoiceId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    public SupplierPaymentAllocation(Long supplierPaymentId, Long supplierInvoiceId, BigDecimal amount) {
        this.supplierPaymentId = supplierPaymentId;
        this.supplierInvoiceId = supplierInvoiceId;
        this.amount = amount;
    }
}

package com.orbix.engine.modules.procurement.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Junction row tying a supplier_invoice to a GRN it covers (with the allocated amount). DATA-MODEL.md §5.8. */
@Entity
@Table(name = "supplier_invoice_grn")
@IdClass(SupplierInvoiceGrnId.class)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = {"supplierInvoiceId", "grnId"})
public class SupplierInvoiceGrn {

    @Id
    @Column(name = "supplier_invoice_id")
    private Long supplierInvoiceId;

    @Id
    @Column(name = "grn_id")
    private Long grnId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    public SupplierInvoiceGrn(Long supplierInvoiceId, Long grnId, BigDecimal amount) {
        this.supplierInvoiceId = supplierInvoiceId;
        this.grnId = grnId;
        this.amount = amount;
    }
}

package com.orbix.engine.modules.procurement.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Links a vendor_credit_note to the supplier_invoice it (partially) settles. Slice H.1. */
@Entity
@Table(name = "vendor_credit_note_allocation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class VendorCreditNoteAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vendor_credit_note_allocation_seq")
    @SequenceGenerator(name = "vendor_credit_note_allocation_seq",
        sequenceName = "vendor_credit_note_allocation_seq", allocationSize = 50)
    private Long id;

    @Column(name = "vendor_credit_note_id", nullable = false)
    private Long vendorCreditNoteId;

    @Column(name = "supplier_invoice_id", nullable = false)
    private Long supplierInvoiceId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "allocated_at", nullable = false)
    private Instant allocatedAt;

    @Column(name = "allocated_by", nullable = false)
    private Long allocatedBy;
}

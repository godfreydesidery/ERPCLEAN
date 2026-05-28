package com.orbix.engine.modules.sales.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Links a customer_credit_note to one sales_invoice it settles. Slice H. */
@Entity
@Table(name = "customer_credit_note_allocation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class CustomerCreditNoteAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_credit_note_allocation_seq")
    @SequenceGenerator(name = "customer_credit_note_allocation_seq",
        sequenceName = "customer_credit_note_allocation_seq", allocationSize = 50)
    private Long id;

    @Column(name = "customer_credit_note_id", nullable = false)
    private Long customerCreditNoteId;

    @Column(name = "sales_invoice_id", nullable = false)
    private Long salesInvoiceId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "allocated_at", nullable = false)
    private Instant allocatedAt;

    @Column(name = "allocated_by", nullable = false)
    private Long allocatedBy;
}

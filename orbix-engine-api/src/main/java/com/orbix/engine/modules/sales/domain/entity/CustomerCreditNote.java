package com.orbix.engine.modules.sales.domain.entity;

import com.orbix.engine.modules.sales.domain.enums.CreditNoteStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Customer credit note — issued for a return, goodwill, or pricing correction. DATA-MODEL.md §6.9. */
@Entity
@Table(name = "customer_credit_note",
    uniqueConstraints = @UniqueConstraint(name = "uk_customer_credit_note_branch_number",
        columnNames = {"branch_id", "number"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class CustomerCreditNote {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_credit_note_seq")
    @SequenceGenerator(name = "customer_credit_note_seq",
        sequenceName = "customer_credit_note_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_return_id")
    private Long customerReturnId;

    @Column(name = "cn_date", nullable = false)
    private LocalDate cnDate;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "allocated_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal allocatedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CreditNoteStatus status = CreditNoteStatus.POSTED;

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
    public CustomerCreditNote(String number, Long companyId, Long branchId, Long customerId,
                              Long customerReturnId, LocalDate cnDate, String currencyCode,
                              BigDecimal totalAmount, String notes, Long actorId) {
        this.number = number;
        this.companyId = companyId;
        this.branchId = branchId;
        this.customerId = customerId;
        this.customerReturnId = customerReturnId;
        this.cnDate = cnDate;
        this.currencyCode = currencyCode;
        this.totalAmount = totalAmount;
        this.allocatedAmount = BigDecimal.ZERO;
        this.status = CreditNoteStatus.POSTED;
        this.notes = notes;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }
}

package com.orbix.engine.modules.procurement.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.procurement.domain.enums.VendorCreditNoteStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Vendor credit note — issued from a vendor return, applied against supplier invoices. DATA-MODEL.md §5.x (Slice H.1). */
@Entity
@Table(name = "vendor_credit_note",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_vendor_credit_note_uid", columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_vendor_credit_note_branch_number",
            columnNames = {"branch_id", "number"})
    })
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class VendorCreditNote extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "vendor_credit_note_seq")
    @SequenceGenerator(name = "vendor_credit_note_seq",
        sequenceName = "vendor_credit_note_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "vendor_return_id")
    private Long vendorReturnId;

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
    private VendorCreditNoteStatus status = VendorCreditNoteStatus.POSTED;

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
    public VendorCreditNote(String number, Long companyId, Long branchId, Long supplierId,
                            Long vendorReturnId, LocalDate cnDate, String currencyCode,
                            BigDecimal totalAmount, String notes, Long actorId) {
        this.number = number;
        this.companyId = companyId;
        this.branchId = branchId;
        this.supplierId = supplierId;
        this.vendorReturnId = vendorReturnId;
        this.cnDate = cnDate;
        this.currencyCode = currencyCode;
        this.totalAmount = totalAmount;
        this.allocatedAmount = BigDecimal.ZERO;
        this.status = VendorCreditNoteStatus.POSTED;
        this.notes = notes;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }
}

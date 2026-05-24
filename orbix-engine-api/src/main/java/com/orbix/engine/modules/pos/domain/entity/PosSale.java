package com.orbix.engine.modules.pos.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.pos.domain.enums.PosSaleKind;
import com.orbix.engine.modules.pos.domain.enums.PosSaleStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** POS sale — header. DATA-MODEL.md §7.3 + §17.12 (Phase 1.1 additions). */
@Entity
@Table(name = "pos_sale",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_pos_sale_uid",            columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_pos_sale_company_number", columnNames = {"company_id", "number"}),
        @UniqueConstraint(name = "uk_pos_sale_client_op",       columnNames = {"company_id", "client_op_id"})
    })
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class PosSale extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pos_sale_seq")
    @SequenceGenerator(name = "pos_sale_seq", sequenceName = "pos_sale_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 60)
    private String number;

    @Column(name = "client_op_id", nullable = false, length = 40)
    private String clientOpId;

    @Column(name = "till_session_id", nullable = false)
    private Long tillSessionId;

    @Column(name = "till_id", nullable = false)
    private Long tillId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    @Column(name = "supervisor_id")
    private Long supervisorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PosSaleKind kind = PosSaleKind.SALE;

    @Column(name = "refunded_from_sale_id")
    private Long refundedFromSaleId;

    @Column(name = "sale_at", nullable = false)
    private Instant saleAt;

    @Column(name = "server_at", nullable = false)
    private Instant serverAt;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "subtotal_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "tendered_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal tenderedAmount;

    @Column(name = "change_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal changeAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PosSaleStatus status = PosSaleStatus.POSTED;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "void_reason", length = 200)
    private String voidReason;

    @Column(name = "fiscal_signature", length = 200)
    private String fiscalSignature;

    @Column(length = 2000)
    private String notes;

    @Version
    private Integer version;

    @SuppressWarnings("java:S107")  // POS-sale header is inherently wide
    public PosSale(String number, String clientOpId, Long tillSessionId, Long tillId, Long branchId,
                   Long companyId, Long sectionId, Long customerId, Long cashierId, Long supervisorId,
                   PosSaleKind kind, Instant saleAt, LocalDate businessDate,
                   BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total,
                   BigDecimal tendered, BigDecimal change, String notes) {
        this.number = number;
        this.clientOpId = clientOpId;
        this.tillSessionId = tillSessionId;
        this.tillId = tillId;
        this.branchId = branchId;
        this.companyId = companyId;
        this.sectionId = sectionId;
        this.customerId = customerId;
        this.cashierId = cashierId;
        this.supervisorId = supervisorId;
        this.kind = kind;
        this.saleAt = saleAt;
        this.serverAt = Instant.now();
        this.businessDate = businessDate;
        this.subtotalAmount = subtotal;
        this.discountAmount = discount;
        this.taxAmount = tax;
        this.totalAmount = total;
        this.tenderedAmount = tendered;
        this.changeAmount = change;
        this.status = PosSaleStatus.POSTED;
        this.notes = notes;
    }

    public void voidSale(String reason, Long actorId) {
        if (status != PosSaleStatus.POSTED) {
            throw new IllegalStateException("Only POSTED sales can be voided (was " + status + ")");
        }
        this.status = PosSaleStatus.VOIDED;
        this.voidedAt = Instant.now();
        this.voidedBy = actorId;
        this.voidReason = reason;
    }
}

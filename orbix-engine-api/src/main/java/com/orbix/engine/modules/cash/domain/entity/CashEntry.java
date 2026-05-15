package com.orbix.engine.modules.cash.domain.entity;

import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Append-only ledger row — one cash movement IN or OUT of a physical account.
 * DATA-MODEL.md §10.2. Immutable: no setters on business fields, no version,
 * no updated_at. UNIQUE {@code (ref_type, ref_id, direction)} is the
 * idempotency key — producers calling the ledger twice for the same source
 * doc resolve to the same triple and the constraint kicks in.
 */
@Entity
@Table(name = "cash_entry",
    uniqueConstraints = @UniqueConstraint(name = "uk_cash_entry_ref",
        columnNames = {"ref_type", "ref_id", "direction"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class CashEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cash_entry_seq")
    @SequenceGenerator(name = "cash_entry_seq", sequenceName = "cash_entry_seq", allocationSize = 50)
    @Setter
    private Long id;

    @Column(nullable = false)
    private Instant at;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CashAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CashDirection direction;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "ref_type", nullable = false, length = 40)
    private String refType;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gl_category", nullable = false, length = 40)
    private GlCategory glCategory;

    @Column(length = 2000)
    private String notes;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @SuppressWarnings("java:S107")  // posting row is inherently wide
    public CashEntry(Instant at, Long companyId, Long branchId, LocalDate businessDate,
                     CashAccount account, CashDirection direction, BigDecimal amount,
                     String currencyCode, String refType, Long refId,
                     GlCategory glCategory, String notes, Long actorId) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("cash_entry amount must be > 0; direction carries the sign");
        }
        this.at = at;
        this.companyId = companyId;
        this.branchId = branchId;
        this.businessDate = businessDate;
        this.account = account;
        this.direction = direction;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.refType = refType;
        this.refId = refId;
        this.glCategory = glCategory;
        this.notes = notes;
        this.actorId = actorId;
        this.createdAt = Instant.now();
    }
}

package com.orbix.engine.modules.cash.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Daily summary per branch / account / currency. Write-through projection —
 * every {@code cash_entry} insert upserts the matching row in the same
 * transaction; always satisfies {@code closing = opening + in − out}.
 * DATA-MODEL.md §10.3 + Phase 1.1 §202. Amounts are in the row's own
 * {@code currency_code} (the tender currency), so per-currency variance
 * (US-DAY-006) is a direct row read with no FX involved.
 *
 * <p>Composite-PK aggregate per ADR 0002 (Path A): the
 * {@code (branchId, account, currencyCode, businessDate)} composite stays
 * the database identity and the join key for every cross-aggregate FK;
 * {@code uid} (inherited from {@link UidEntity}) is the external URL handle
 * only.
 */
@Entity
@Table(
    name = "cash_book",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_cash_book_uid", columnNames = {"uid"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class CashBook extends UidEntity {

    @EmbeddedId
    private CashBookId id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "opening_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal openingAmount = BigDecimal.ZERO;

    @Column(name = "in_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal inAmount = BigDecimal.ZERO;

    @Column(name = "out_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal outAmount = BigDecimal.ZERO;

    @Column(name = "closing_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal closingAmount = BigDecimal.ZERO;

    @Version
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CashBook(CashBookId id, Long companyId, BigDecimal openingAmount) {
        this.id = id;
        this.companyId = companyId;
        this.openingAmount = openingAmount != null ? openingAmount : BigDecimal.ZERO;
        this.inAmount = BigDecimal.ZERO;
        this.outAmount = BigDecimal.ZERO;
        this.closingAmount = this.openingAmount;
        this.updatedAt = Instant.now();
    }

    public Long getBranchId() { return id.getBranchId(); }
    public com.orbix.engine.modules.cash.domain.enums.CashAccount getAccount() { return id.getAccount(); }
    public String getCurrencyCode() { return id.getCurrencyCode(); }
    public LocalDate getBusinessDate() { return id.getBusinessDate(); }

    public void addIn(BigDecimal amount) {
        this.inAmount = this.inAmount.add(amount);
        recomputeClosing();
    }

    public void addOut(BigDecimal amount) {
        this.outAmount = this.outAmount.add(amount);
        recomputeClosing();
    }

    private void recomputeClosing() {
        this.closingAmount = this.openingAmount.add(this.inAmount).subtract(this.outAmount);
        this.updatedAt = Instant.now();
    }
}

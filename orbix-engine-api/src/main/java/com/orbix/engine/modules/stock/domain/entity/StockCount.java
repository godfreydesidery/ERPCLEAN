package com.orbix.engine.modules.stock.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.stock.domain.enums.StockCountStatus;
import com.orbix.engine.modules.stock.domain.enums.StockCountType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/** A physical count session. DRAFT -> IN_PROGRESS -> CLOSED -> POSTED. DATA-MODEL.md §4.3. */
@Entity
@Table(name = "stock_count",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_count_uid", columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_stock_count_branch_number",
            columnNames = {"branch_id", "number"})
    })
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class StockCount extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stock_count_seq")
    @SequenceGenerator(name = "stock_count_seq", sequenceName = "stock_count_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "count_date", nullable = false)
    private LocalDate countDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockCountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StockCountStatus status = StockCountStatus.DRAFT;

    @Column(name = "started_by", nullable = false)
    private Long startedBy;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    public StockCount(String number, Long branchId, Long companyId, LocalDate countDate,
                      StockCountType type, Long startedBy) {
        this.number = number;
        this.branchId = branchId;
        this.companyId = companyId;
        this.countDate = countDate;
        this.type = type;
        this.startedBy = startedBy;
        this.status = StockCountStatus.DRAFT;
    }

    public void start() {
        requireStatus(StockCountStatus.DRAFT);
        this.status = StockCountStatus.IN_PROGRESS;
    }

    public void close(Long actorId) {
        requireStatus(StockCountStatus.IN_PROGRESS);
        this.status = StockCountStatus.CLOSED;
        this.closedBy = actorId;
    }

    public void post() {
        requireStatus(StockCountStatus.CLOSED);
        this.status = StockCountStatus.POSTED;
        this.postedAt = Instant.now();
    }

    private void requireStatus(StockCountStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Stock count is " + status + ", expected " + expected);
        }
    }
}

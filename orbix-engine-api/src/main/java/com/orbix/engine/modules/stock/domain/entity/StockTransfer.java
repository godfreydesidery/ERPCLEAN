package com.orbix.engine.modules.stock.domain.entity;

import com.orbix.engine.modules.stock.domain.enums.StockTransferStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Inter-branch transfer. DRAFT -> ISSUED -> RECEIVED -> CLOSED. DATA-MODEL.md §4.5. */
@Entity
@Table(name = "stock_transfer",
    uniqueConstraints = @UniqueConstraint(name = "uk_stock_transfer_company_number",
        columnNames = {"company_id", "number"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class StockTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stock_transfer_seq")
    @SequenceGenerator(name = "stock_transfer_seq", sequenceName = "stock_transfer_seq",
        allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "from_branch_id", nullable = false)
    private Long fromBranchId;

    @Column(name = "to_branch_id", nullable = false)
    private Long toBranchId;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StockTransferStatus status = StockTransferStatus.DRAFT;

    public StockTransfer(String number, Long companyId, Long fromBranchId, Long toBranchId) {
        this.number = number;
        this.companyId = companyId;
        this.fromBranchId = fromBranchId;
        this.toBranchId = toBranchId;
        this.status = StockTransferStatus.DRAFT;
    }

    public void issue() {
        requireStatus(StockTransferStatus.DRAFT);
        this.status = StockTransferStatus.ISSUED;
        this.issuedAt = Instant.now();
    }

    public void receive() {
        requireStatus(StockTransferStatus.ISSUED);
        this.status = StockTransferStatus.RECEIVED;
        this.receivedAt = Instant.now();
    }

    public void close() {
        requireStatus(StockTransferStatus.RECEIVED);
        this.status = StockTransferStatus.CLOSED;
    }

    private void requireStatus(StockTransferStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Stock transfer is " + status + ", expected " + expected);
        }
    }
}

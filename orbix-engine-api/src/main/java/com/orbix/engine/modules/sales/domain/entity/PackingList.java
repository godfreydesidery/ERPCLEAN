package com.orbix.engine.modules.sales.domain.entity;

import com.orbix.engine.modules.sales.domain.enums.PackingListStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/** Packing list — header. DATA-MODEL.md §6.10. */
@Entity
@Table(name = "packing_list",
    uniqueConstraints = @UniqueConstraint(name = "uk_packing_list_branch_number",
        columnNames = {"branch_id", "number"}))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class PackingList {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "packing_list_seq")
    @SequenceGenerator(name = "packing_list_seq", sequenceName = "packing_list_seq",
        allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "sales_invoice_id", nullable = false)
    private Long salesInvoiceId;

    @Column(name = "dispatch_date", nullable = false)
    private LocalDate dispatchDate;

    @Column(name = "driver_name", length = 120)
    private String driverName;

    @Column(name = "vehicle_no", length = 40)
    private String vehicleNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PackingListStatus status = PackingListStatus.DRAFT;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "delivered_by")
    private Long deliveredBy;

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
    public PackingList(String number, Long companyId, Long branchId, Long salesInvoiceId,
                      LocalDate dispatchDate, String driverName, String vehicleNo, String notes,
                      Long actorId) {
        this.number = number;
        this.companyId = companyId;
        this.branchId = branchId;
        this.salesInvoiceId = salesInvoiceId;
        this.dispatchDate = dispatchDate;
        this.driverName = driverName;
        this.vehicleNo = vehicleNo;
        this.notes = notes;
        this.status = PackingListStatus.DRAFT;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void dispatch(Long actorId) {
        requireStatus(PackingListStatus.DRAFT);
        this.status = PackingListStatus.DISPATCHED;
        touch(actorId);
    }

    public void markDelivered(Long actorId) {
        requireStatus(PackingListStatus.DISPATCHED);
        this.status = PackingListStatus.DELIVERED;
        this.deliveredAt = Instant.now();
        this.deliveredBy = actorId;
        touch(actorId);
    }

    public void cancel(Long actorId) {
        requireStatus(PackingListStatus.DRAFT);
        this.status = PackingListStatus.CANCELLED;
        touch(actorId);
    }

    private void requireStatus(PackingListStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Packing list is " + status + ", expected " + expected);
        }
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}

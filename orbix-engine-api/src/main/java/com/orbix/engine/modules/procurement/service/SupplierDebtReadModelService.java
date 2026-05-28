package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.procurement.domain.dto.SupplierAgingDto;
import com.orbix.engine.modules.procurement.domain.dto.SupplierDunningQueueRowDto;
import com.orbix.engine.modules.procurement.domain.dto.SupplierStatementDto;
import com.orbix.engine.modules.sales.domain.enums.AgingBucket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * Slice G.1 — AP debt read model: aging buckets, dunning queue, and
 * single-supplier statement. Mirrors {@code DebtReadModelService} on the AR side.
 */
public interface SupplierDebtReadModelService {

    /**
     * AP aging report: all open supplier invoices grouped by supplier into
     * 5 overdue buckets, totalled. {@code branchId} null = company-wide.
     */
    SupplierAgingDto aging(Long branchId, LocalDate asOf);

    /**
     * Paged AP dunning queue: one row per supplier with overdue exposure,
     * optionally filtered to a single {@link AgingBucket}. Default sort is
     * oldest-overdue desc.
     */
    Page<SupplierDunningQueueRowDto> dunning(Long branchId, AgingBucket bucketFilter, Pageable pageable);

    /**
     * Supplier statement / drill-down: open invoices (max 100, dueDate asc) +
     * recent payments (last 30 days, max 50) + per-bucket aging row.
     * Supplier addressed by party uid.
     */
    SupplierStatementDto supplierStatement(String supplierUid);
}

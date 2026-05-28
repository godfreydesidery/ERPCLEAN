package com.orbix.engine.modules.procurement.domain.dto;

import com.orbix.engine.modules.sales.domain.enums.AgingBucket;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Slice G.1 — single row of the AP dunning queue. Operator-shaped (one row
 * per supplier with overdue exposure, default sort by oldest-overdue desc).
 */
public record SupplierDunningQueueRowDto(
    Long supplierId,
    String supplierUid,
    String supplierName,
    BigDecimal totalOutstanding,
    Integer oldestDaysOverdue,
    LocalDate oldestDueDate,
    AgingBucket worstBucket,
    long overdueInvoiceCount
) {}

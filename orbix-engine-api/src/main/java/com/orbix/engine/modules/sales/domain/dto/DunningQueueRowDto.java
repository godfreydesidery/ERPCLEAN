package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.enums.AgingBucket;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Slice G — single row of the dunning queue. Operator-shaped (one row per
 * customer with overdue exposure, default sort by oldest-overdue desc).
 */
public record DunningQueueRowDto(
    Long customerId,
    String customerUid,
    String customerName,
    BigDecimal creditLimit,
    BigDecimal totalOutstanding,
    Integer oldestDaysOverdue,
    LocalDate oldestDueDate,
    AgingBucket worstBucket,
    long overdueInvoiceCount
) {}

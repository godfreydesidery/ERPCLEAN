package com.orbix.engine.modules.orders.domain.dto;

import com.orbix.engine.modules.orders.domain.enums.CustomerOrderStatus;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of the per-order drill-down inside the layby ageing report
 * (F8.6 / US-RPT-014). Surfaces the fields the operator needs to chase
 * the customer: number, customer id, age, balance, reserved-until.
 */
public record LaybyAgeingOrderDto(
    Long id,
    String number,
    Long branchId,
    Long customerId,
    CustomerOrderType type,
    CustomerOrderStatus status,
    Instant createdAt,
    Instant reservedUntil,
    int ageDays,
    Integer daysUntilExpiry,
    BigDecimal totalAmount,
    BigDecimal paidAmount,
    BigDecimal balanceDue
) {}

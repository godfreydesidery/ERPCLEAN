package com.orbix.engine.modules.orders.service;

import com.orbix.engine.modules.orders.domain.dto.LaybyAgeingReportDto;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderType;

import java.time.Instant;

/**
 * Layby / pre-order ageing rollup (F8.6 / US-RPT-014). Returns open
 * orders bucketed by days-since-create with per-type top-line balances
 * for the dashboard plus a flat drill-down sorted oldest-first.
 *
 * <p>Optional {@code branchId} scope; optional {@code type} filter
 * (LAYBY / PRE_ORDER); optional {@code asOf} override for the age
 * computation — defaults to "now".
 */
public interface LaybyAgeingReportService {

    LaybyAgeingReportDto report(Long branchId, CustomerOrderType type, Instant asOf);
}

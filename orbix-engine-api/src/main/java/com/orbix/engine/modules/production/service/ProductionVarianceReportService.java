package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.production.domain.dto.ProductionVarianceRowDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Production variance report (F7.4 / US-PROD-008 / TC-PROD-021). One row per
 * batch carrying planned vs actual output, total consumption cost (rolled up
 * from {@code production_consumption}), and wastage breakdown by
 * {@link com.orbix.engine.modules.production.domain.enums.WastageCategory}.
 *
 * <p>All filters are optional; null = no filter. Date filters compare against
 * {@code production_batch.planned_at}.
 */
public interface ProductionVarianceReportService {

    List<ProductionVarianceRowDto> report(Long branchId, Long sectionId, Long bomId,
                                          LocalDate from, LocalDate to);
}

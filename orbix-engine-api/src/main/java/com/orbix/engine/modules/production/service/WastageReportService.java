package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.production.domain.dto.WastageReportRowDto;
import com.orbix.engine.modules.production.domain.enums.WastageCategory;

import java.time.LocalDate;
import java.util.List;

/**
 * Production wastage rollup (F8.4 / US-RPT-012). Per-(section, category)
 * totals over a window — total qty, best-effort total cost, distinct-item
 * count, and record count. Complements the per-batch variance side that
 * already ships at {@code /api/v1/reports/production-variance} (F7.4).
 *
 * <p>All filters optional; date range defaults to the last 30 days when
 * {@code from} / {@code to} are omitted. {@code branchId = null} scopes
 * the whole company.
 */
public interface WastageReportService {

    List<WastageReportRowDto> report(Long branchId, Long sectionId, WastageCategory category,
                                     LocalDate from, LocalDate to);
}

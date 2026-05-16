package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.sales.domain.dto.SectionPnlRowDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Section P&L (F8.3 / US-RPT-011). Per-section revenue (POS net), COGS,
 * gross margin, and production wastage drag for a (branch, period). All
 * filters optional except date range; {@code branchId = null} scopes the
 * whole company.
 */
public interface SectionPnlReportService {

    List<SectionPnlRowDto> report(Long branchId, LocalDate from, LocalDate to);
}

package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.common.domain.dto.DashboardRollupDto;

import java.time.LocalDate;

/**
 * Slice F — consolidated dashboard rollup. Cross-module read-only orchestrator
 * that aggregates the five live dashboard tile feeds + four alert counts into
 * a single payload. Lives in {@code common.service} because it spans modules
 * and is purely read-only; no outbox events, no state mutations.
 *
 * <p>Each fragment is gated by the same permission as its underlying
 * single-purpose endpoint (e.g. AR section requires
 * {@code SALES.REPORT.AR_SUMMARY}). When the caller lacks a permission the
 * service catches {@link org.springframework.security.access.AccessDeniedException}
 * and serialises that fragment as {@code null} — the dashboard renders
 * {@code null} as "Permission required", matching today's per-tile UX.
 */
public interface DashboardReportService {

    /**
     * @param branchId optional — null = company-wide (subject to
     *                 {@code BranchScope.requireReadable})
     * @param businessDate optional — defaults to today (server time, TZ Africa/Dar_es_Salaam)
     */
    DashboardRollupDto rollup(Long branchId, LocalDate businessDate);
}

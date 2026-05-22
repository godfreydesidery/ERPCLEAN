package com.orbix.engine.modules.day.service;

/**
 * One reason a business day cannot be closed. Returned by {@link EodGuard}
 * implementations from the posting modules (POS, procurement, production, …)
 * and aggregated by {@code BusinessDayServiceImpl.startClosing}. The day
 * service rejects close with one 422 carrying the full list so the operator
 * can resolve all blockers in one pass instead of repeating the call.
 *
 * @param module  source module ({@code pos}, {@code procurement}, {@code production})
 * @param kind    machine-readable blocker category, e.g. {@code OPEN_TILL_SESSION},
 *                {@code DRAFT_GRN}, {@code IN_PROGRESS_BATCH}
 * @param refType the offending aggregate type (e.g. {@code TillSession}, {@code Grn})
 * @param refId   the offending aggregate id
 * @param message human-readable summary including the aggregate's identifier
 */
public record EodBlockerDto(
    String module,
    String kind,
    String refType,
    Long refId,
    String message
) {}

package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.cash.domain.dto.CashAdjustmentDto;
import com.orbix.engine.modules.cash.domain.dto.PostCashAdjustmentRequestDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Supervisor cash adjustments (F6.3 / TC-CASH-013). Each adjustment is a
 * single {@code cash_entry} with {@code gl_category = ADJUSTMENT}, written
 * in the same transaction as the {@code cash_adjustment} audit row. The
 * controller gates on {@code CASH.ADJUST}; the business day must be OPEN.
 */
public interface CashAdjustmentService {

    CashAdjustmentDto post(PostCashAdjustmentRequestDto request);

    List<CashAdjustmentDto> list(Long branchId, LocalDate businessDate);
}

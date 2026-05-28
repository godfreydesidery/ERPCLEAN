package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.cash.domain.dto.CashAdjustmentDto;
import com.orbix.engine.modules.cash.domain.dto.PostCashAdjustmentRequestDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Supervisor cash adjustments (F6.3 / TC-CASH-013). Each adjustment is a
 * single {@code cash_entry} with {@code gl_category = ADJUSTMENT}, written
 * in the same transaction as the {@code cash_adjustment} audit row. The
 * controller gates on {@code CASH.ADJUSTMENT.POST}; the business day must
 * be OPEN.
 *
 * <p>Slice D — adjustments carry a {@code uid} URL handle and a reversal
 * lifecycle: {@link #archiveCashAdjustmentByUid(String)} posts a
 * compensating cash entry under {@code CASH_ADJUSTMENT_REVERSAL} and
 * stamps the audit-doc's reversal columns. Reversal is single-shot.
 */
public interface CashAdjustmentService {

    CashAdjustmentDto post(PostCashAdjustmentRequestDto request);

    List<CashAdjustmentDto> list(Long branchId, LocalDate businessDate);

    /** uid-keyed read; tenant-checked. */
    CashAdjustmentDto getCashAdjustmentByUid(String uid);

    /**
     * Reverse a posted adjustment by uid. Throws if the row was already
     * reversed or belongs to a different tenant. Emits
     * {@code CashAdjustmentReversed.v1}.
     */
    CashAdjustmentDto archiveCashAdjustmentByUid(String uid);
}

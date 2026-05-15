package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.procurement.domain.dto.CreateGrnRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.GrnDto;

import java.util.List;

/**
 * GRN lifecycle (F3.2). Create a DRAFT GRN (against an APPROVED LPO or as a
 * direct GRN under {@code GRN.DIRECT}), then post to write the {@code stock_move}
 * rows (and {@code stock_batch} rows for batch-tracked items) atomically with
 * the flip to POSTED. Posting requires the receiving branch's day to be OPEN.
 */
public interface GrnService {

    GrnDto createDraft(CreateGrnRequestDto request);

    /** DRAFT → POSTED: writes stock moves + batches, advances LPO line received_qty + LPO status. */
    GrnDto post(Long grnId);

    /** DRAFT → CANCELLED. */
    GrnDto cancel(Long grnId);

    List<GrnDto> list(Long branchId);

    GrnDto get(Long grnId);
}

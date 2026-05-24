package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.procurement.domain.dto.CreateGrnRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.GrnDto;
import org.springframework.data.domain.Pageable;

/**
 * GRN lifecycle (F3.2). Create a DRAFT GRN (against an APPROVED LPO or as a
 * direct GRN under {@code GRN.DIRECT}), then post to write the {@code stock_move}
 * rows (and {@code stock_batch} rows for batch-tracked items) atomically with
 * the flip to POSTED. Posting requires the receiving branch's day to be OPEN.
 */
public interface GrnService {

    GrnDto createDraft(CreateGrnRequestDto request);

    /** DRAFT → POSTED: writes stock moves + batches, advances LPO line received_qty + LPO status. */
    GrnDto post(String uid);

    /** DRAFT → CANCELLED. */
    GrnDto cancel(String uid);

    PageDto<GrnDto> list(Long branchId, Pageable pageable);

    GrnDto get(String uid);
}

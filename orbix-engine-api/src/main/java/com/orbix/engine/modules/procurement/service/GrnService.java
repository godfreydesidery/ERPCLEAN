package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.procurement.domain.dto.CreateGrnRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.GrnDto;
import com.orbix.engine.modules.procurement.domain.enums.GrnStatus;
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

    /** DRAFT → CANCELLED with an optional reason. Gated by {@code GRN.POST}. */
    GrnDto cancel(String uid, String reason);

    /**
     * POSTED → CANCELLED with compensating semantics. Posts opposite-direction
     * {@code stock_move} rows for every GRN line, rewinds the LPO line
     * {@code received_qty}, and flips the LPO header status back if appropriate.
     * Reason is required (validated at the controller). Gated by {@code GRN.CANCEL}.
     */
    GrnDto cancelPosted(String uid, String reason);

    /**
     * Paginated GRN list. All filter params are optional (null = no filter).
     * {@code branchId} is the branch override from the request; scoping is
     * enforced by {@code BranchScope} inside the impl.
     */
    PageDto<GrnDto> list(Long branchId, Long supplierId, GrnStatus status, Pageable pageable);

    GrnDto get(String uid);
}

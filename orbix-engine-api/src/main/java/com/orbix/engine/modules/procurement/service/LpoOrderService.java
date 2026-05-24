package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.procurement.domain.dto.CreateLpoOrderRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.LpoOrderDto;
import com.orbix.engine.modules.procurement.domain.dto.UpdateLpoOrderRequestDto;
import org.springframework.data.domain.Pageable;

/**
 * LPO lifecycle (F3.1). State machine:
 * DRAFT → PENDING_APPROVAL → APPROVED. Submitting an LPO whose total is at or
 * below the configured auto-approval threshold skips PENDING_APPROVAL.
 * DRAFT / PENDING_APPROVAL may be CANCELLED. PARTIALLY_RECEIVED / RECEIVED
 * transitions land with F3.2 (GRN).
 */
public interface LpoOrderService {

    LpoOrderDto createDraft(CreateLpoOrderRequestDto request);

    LpoOrderDto updateDraft(String uid, UpdateLpoOrderRequestDto request);

    /** Moves DRAFT → PENDING_APPROVAL (or DRAFT → APPROVED if total ≤ threshold). */
    LpoOrderDto submit(String uid);

    /** Moves PENDING_APPROVAL → APPROVED. Gated by {@code PROCUREMENT.APPROVE_LPO} at the controller. */
    LpoOrderDto approve(String uid);

    /** Moves DRAFT / PENDING_APPROVAL → CANCELLED. */
    LpoOrderDto cancel(String uid);

    PageDto<LpoOrderDto> list(Long branchId, Pageable pageable);

    LpoOrderDto get(String uid);
}

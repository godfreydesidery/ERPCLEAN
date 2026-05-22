package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.procurement.domain.dto.CreateLpoOrderRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.LpoOrderDto;
import com.orbix.engine.modules.procurement.domain.dto.UpdateLpoOrderRequestDto;

import java.util.List;

/**
 * LPO lifecycle (F3.1). State machine:
 * DRAFT → PENDING_APPROVAL → APPROVED. Submitting an LPO whose total is at or
 * below the configured auto-approval threshold skips PENDING_APPROVAL.
 * DRAFT / PENDING_APPROVAL may be CANCELLED. PARTIALLY_RECEIVED / RECEIVED
 * transitions land with F3.2 (GRN).
 */
public interface LpoOrderService {

    LpoOrderDto createDraft(CreateLpoOrderRequestDto request);

    LpoOrderDto updateDraft(Long lpoId, UpdateLpoOrderRequestDto request);

    /** Moves DRAFT → PENDING_APPROVAL (or DRAFT → APPROVED if total ≤ threshold). */
    LpoOrderDto submit(Long lpoId);

    /** Moves PENDING_APPROVAL → APPROVED. Gated by {@code PROCUREMENT.APPROVE_LPO} at the controller. */
    LpoOrderDto approve(Long lpoId);

    /** Moves DRAFT / PENDING_APPROVAL → CANCELLED. */
    LpoOrderDto cancel(Long lpoId);

    List<LpoOrderDto> list(Long branchId);

    LpoOrderDto get(Long lpoId);
}

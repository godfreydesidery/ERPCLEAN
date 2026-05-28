package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.procurement.domain.dto.CreateLpoOrderRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.LpoOrderDto;
import com.orbix.engine.modules.procurement.domain.dto.UpdateLpoOrderRequestDto;
import com.orbix.engine.modules.procurement.domain.enums.LpoOrderStatus;
import org.springframework.data.domain.Pageable;

/**
 * LPO lifecycle (F3.1). State machine:
 * DRAFT → PENDING_APPROVAL → APPROVED. Submitting an LPO whose total is at or
 * below the configured auto-approval threshold skips PENDING_APPROVAL.
 * DRAFT / PENDING_APPROVAL may be CANCELLED via {@code PROCUREMENT.MANAGE_LPO};
 * APPROVED may be cancelled via {@code PROCUREMENT.CANCEL_LPO} provided no GRN
 * draws against the LPO. PARTIALLY_RECEIVED / RECEIVED transitions land with
 * F3.2 (GRN); their cancellation is deferred to Slice C.
 */
public interface LpoOrderService {

    LpoOrderDto createDraft(CreateLpoOrderRequestDto request);

    LpoOrderDto updateDraft(String uid, UpdateLpoOrderRequestDto request);

    /** Moves DRAFT → PENDING_APPROVAL (or DRAFT → APPROVED if total ≤ threshold). */
    LpoOrderDto submit(String uid);

    /** Moves PENDING_APPROVAL → APPROVED. Gated by {@code PROCUREMENT.APPROVE_LPO} at the controller. */
    LpoOrderDto approve(String uid);

    /**
     * Cancel the LPO with an optional reason. Allowed:
     * DRAFT / PENDING_APPROVAL → CANCELLED at any time;
     * APPROVED → CANCELLED only when no GRN draws against this LPO.
     */
    LpoOrderDto cancel(String uid, String reason);

    /**
     * Slice F — drill-through-friendly list. {@code status} is optional; when
     * null, the result is the full company / branch list ordered by id desc;
     * when set, it filters to LPOs in that status.
     */
    PageDto<LpoOrderDto> list(Long branchId, LpoOrderStatus status, Pageable pageable);

    LpoOrderDto get(String uid);

    /**
     * Count of LPOs sitting in PENDING_APPROVAL for a branch (or company-wide
     * when {@code branchId} is null). Used by the dashboard's "Approvals
     * pending" tile.
     */
    long countPendingApproval(Long branchId);
}

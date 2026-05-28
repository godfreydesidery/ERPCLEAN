package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.procurement.domain.dto.ApplyVendorCreditNoteRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.CreateVendorReturnRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.IssueVendorCreditNoteRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.VendorCreditNoteDto;
import com.orbix.engine.modules.procurement.domain.dto.VendorReturnDto;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Vendor returns + credit-note allocation (US-PROC-008 + US-PROC-009, Slice H.1).
 * State machines: {@code DRAFT → POSTED → CREDITED}; {@code POSTED → PARTIALLY_ALLOCATED → FULLY_ALLOCATED}.
 */
public interface VendorReturnService {

    /** Create a DRAFT vendor return. */
    VendorReturnDto createDraft(CreateVendorReturnRequestDto request);

    /** DRAFT → POSTED: posts stock-OUT move + emits {@code VendorReturnPosted.v1}. */
    VendorReturnDto post(String uid);

    /** DRAFT → CANCELLED. */
    VendorReturnDto cancel(String uid);

    /** POSTED → CREDITED: creates a {@code vendor_credit_note} + emits {@code VendorCreditNoteIssued.v1}. */
    VendorCreditNoteDto issueCreditNote(String uid, IssueVendorCreditNoteRequestDto request);

    VendorReturnDto get(String uid);

    PageDto<VendorReturnDto> list(Long branchId, Pageable pageable);

    List<VendorCreditNoteDto> listCreditNotes(Long branchId);

    /**
     * Apply a vendor credit note against an open supplier invoice (US-PROC-009).
     * Emits {@code VendorCreditNoteApplied.v1}.
     * ADR-0004 sync-TX exemption #23: credit-note allocation write + supplier
     * invoice paidAmount update happen in the same DB transaction.
     */
    VendorCreditNoteDto applyToInvoice(String creditNoteUid, ApplyVendorCreditNoteRequestDto request);
}

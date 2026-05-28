package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.procurement.domain.dto.ApplyVendorCreditNoteRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.CreateVendorReturnRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.IssueVendorCreditNoteRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.VendorCreditNoteDto;
import com.orbix.engine.modules.procurement.domain.dto.VendorReturnDto;
import com.orbix.engine.modules.procurement.service.VendorReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Vendor returns + credit notes (US-PROC-008 + US-PROC-009). Gated by {@code PROCUREMENT.MANAGE_RETURN}. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('PROCUREMENT.MANAGE_RETURN')")
public class VendorReturnController {

    private final VendorReturnService service;

    @GetMapping("/vendor-returns")
    public PageDto<VendorReturnDto> list(@RequestParam(required = false) Long branchId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.list(branchId, PageRequest.of(page, size));
    }

    @GetMapping("/vendor-returns/uid/{uid}")
    public VendorReturnDto get(@PathVariable @ValidUlid String uid) {
        return service.get(uid);
    }

    @PostMapping("/vendor-returns")
    public ResponseEntity<VendorReturnDto> create(
            @Valid @RequestBody CreateVendorReturnRequestDto request) {
        VendorReturnDto ret = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/vendor-returns/uid/" + ret.uid())).body(ret);
    }

    @PostMapping("/vendor-returns/uid/{uid}/post")
    public VendorReturnDto post(@PathVariable @ValidUlid String uid) {
        return service.post(uid);
    }

    @PostMapping("/vendor-returns/uid/{uid}/cancel")
    public VendorReturnDto cancel(@PathVariable @ValidUlid String uid) {
        return service.cancel(uid);
    }

    @PostMapping("/vendor-returns/uid/{uid}/issue-credit-note")
    public VendorCreditNoteDto issueCreditNote(@PathVariable @ValidUlid String uid,
                                               @Valid @RequestBody IssueVendorCreditNoteRequestDto request) {
        return service.issueCreditNote(uid, request);
    }

    @GetMapping("/vendor-credit-notes")
    public List<VendorCreditNoteDto> listCreditNotes(@RequestParam(required = false) Long branchId) {
        return service.listCreditNotes(branchId);
    }

    /** Slice H.1 — apply a vendor credit note to an open supplier invoice (US-PROC-009). */
    @PostMapping("/vendor-credit-notes/uid/{uid}/apply")
    public VendorCreditNoteDto applyVendorCreditNote(@PathVariable @ValidUlid String uid,
                                                     @Valid @RequestBody ApplyVendorCreditNoteRequestDto request) {
        return service.applyToInvoice(uid, request);
    }
}

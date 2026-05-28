package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.sales.domain.dto.ApplyCreditNoteRequestDto;
import com.orbix.engine.modules.sales.domain.dto.CreateCustomerReturnRequestDto;
import com.orbix.engine.modules.sales.domain.dto.CustomerCreditNoteDto;
import com.orbix.engine.modules.sales.domain.dto.CustomerReturnDto;
import com.orbix.engine.modules.sales.domain.dto.IssueCreditNoteRequestDto;
import com.orbix.engine.modules.sales.service.CustomerReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Customer returns + credit notes (F4.4). Gated by {@code SALES.MANAGE_RETURN}. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('SALES.MANAGE_RETURN')")
public class CustomerReturnController {

    private final CustomerReturnService service;

    @GetMapping("/customer-returns")
    public PageDto<CustomerReturnDto> list(@RequestParam(required = false) Long branchId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.list(branchId, PageRequest.of(page, size));
    }

    @GetMapping("/customer-returns/uid/{uid}")
    public CustomerReturnDto get(@PathVariable @ValidUlid String uid) {
        return service.get(uid);
    }

    @PostMapping("/customer-returns")
    public ResponseEntity<CustomerReturnDto> create(
            @Valid @RequestBody CreateCustomerReturnRequestDto request) {
        CustomerReturnDto ret = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/customer-returns/uid/" + ret.uid())).body(ret);
    }

    @PostMapping("/customer-returns/uid/{uid}/post")
    public CustomerReturnDto post(@PathVariable @ValidUlid String uid) {
        return service.post(uid);
    }

    @PostMapping("/customer-returns/uid/{uid}/cancel")
    public CustomerReturnDto cancel(@PathVariable @ValidUlid String uid) {
        return service.cancel(uid);
    }

    @PostMapping("/customer-returns/uid/{uid}/issue-credit-note")
    public CustomerCreditNoteDto issueCreditNote(@PathVariable @ValidUlid String uid,
                                                 @Valid @RequestBody IssueCreditNoteRequestDto request) {
        return service.issueCreditNote(uid, request);
    }

    @GetMapping("/customer-credit-notes")
    public List<CustomerCreditNoteDto> listCreditNotes(@RequestParam(required = false) Long branchId) {
        return service.listCreditNotes(branchId);
    }

    /** Slice H — apply a credit note to an open invoice for the same customer (US-SALES-011). */
    @PostMapping("/customer-credit-notes/uid/{uid}/apply")
    public CustomerCreditNoteDto applyCreditNote(@PathVariable @ValidUlid String uid,
                                                 @Valid @RequestBody ApplyCreditNoteRequestDto request) {
        return service.applyToInvoice(uid, request);
    }
}

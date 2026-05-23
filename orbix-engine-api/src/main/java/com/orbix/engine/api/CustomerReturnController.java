package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
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
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Customer returns + credit notes (F4.4). Gated by {@code SALES.MANAGE_RETURN}. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SALES.MANAGE_RETURN')")
public class CustomerReturnController {

    private final CustomerReturnService service;

    @GetMapping("/customer-returns")
    public PageDto<CustomerReturnDto> list(@RequestParam(required = false) Long branchId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.list(branchId, PageRequest.of(page, size));
    }

    @GetMapping("/customer-returns/{id}")
    public CustomerReturnDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/customer-returns")
    public ResponseEntity<CustomerReturnDto> create(
            @Valid @RequestBody CreateCustomerReturnRequestDto request) {
        CustomerReturnDto ret = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/customer-returns/" + ret.id())).body(ret);
    }

    @PostMapping("/customer-returns/{id}/post")
    public CustomerReturnDto post(@PathVariable Long id) {
        return service.post(id);
    }

    @PostMapping("/customer-returns/{id}/cancel")
    public CustomerReturnDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @PostMapping("/customer-returns/{id}/issue-credit-note")
    public CustomerCreditNoteDto issueCreditNote(@PathVariable Long id,
                                                 @Valid @RequestBody IssueCreditNoteRequestDto request) {
        return service.issueCreditNote(id, request);
    }

    @GetMapping("/customer-credit-notes")
    public List<CustomerCreditNoteDto> listCreditNotes(@RequestParam(required = false) Long branchId) {
        return service.listCreditNotes(branchId);
    }
}

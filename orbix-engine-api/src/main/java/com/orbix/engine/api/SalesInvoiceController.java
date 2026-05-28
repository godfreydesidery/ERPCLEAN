package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.sales.domain.dto.CreateSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.dto.PostSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.dto.ReprintInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.dto.SalesInvoiceDto;
import com.orbix.engine.modules.sales.domain.dto.VoidSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.service.SalesInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Back-office sales invoices (F4.2). Gated by {@code SALES.MANAGE_INVOICE}
 * with the per-action override / reprint perms layered on top (Slice C).
 * Invoices are addressed externally by their {@code uid} (a ULID) via the
 * literal {@code /uid/{uid}} segment; the numeric {@code id} stays in the body.
 */
@RestController
@RequestMapping("/api/v1/sales-invoices")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('SALES.MANAGE_INVOICE')")
public class SalesInvoiceController {

    private final SalesInvoiceService service;

    /**
     * Slice F — {@code status} optional. Accepts the bucket aliases
     * {@code "OPEN"} / {@code "OVERDUE"} for the dashboard drill-through
     * contract, or any raw {@link com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus}
     * value for exact filtering.
     */
    @GetMapping
    public PageDto<SalesInvoiceDto> list(@RequestParam(required = false) Long branchId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.list(branchId, status, PageRequest.of(page, size));
    }

    @GetMapping("/uid/{uid}")
    public SalesInvoiceDto get(@PathVariable @ValidUlid String uid) {
        return service.get(uid);
    }

    @PostMapping
    public ResponseEntity<SalesInvoiceDto> create(
            @Valid @RequestBody CreateSalesInvoiceRequestDto request) {
        SalesInvoiceDto invoice = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/sales-invoices/uid/" + invoice.uid()))
            .body(invoice);
    }

    /**
     * DRAFT → POSTED. Body is optional; supply
     * {@code { "overrideReason": "..." }} when the caller holds
     * {@code SALES_INVOICE.OVERRIDE_CREDIT} and the customer's credit limit
     * would otherwise block the post (Slice C GAP 3.A / 5.B).
     */
    @PostMapping("/uid/{uid}/post")
    public SalesInvoiceDto post(@PathVariable @ValidUlid String uid,
                                @Valid @RequestBody(required = false) PostSalesInvoiceRequestDto request) {
        return service.post(uid, request != null ? request : PostSalesInvoiceRequestDto.empty());
    }

    @PostMapping("/uid/{uid}/void")
    public SalesInvoiceDto voidInvoice(@PathVariable @ValidUlid String uid,
                                       @Valid @RequestBody VoidSalesInvoiceRequestDto request) {
        return service.voidInvoice(uid, request);
    }

    @PostMapping("/uid/{uid}/cancel")
    public SalesInvoiceDto cancel(@PathVariable @ValidUlid String uid) {
        return service.cancel(uid);
    }

    /**
     * Slice C — reprint audit. Increments {@code reprint_count}, emits
     * {@code SalesInvoiceReprinted.v1}, returns the updated DTO. No state
     * mutation beyond the counter.
     */
    @PostMapping("/uid/{uid}/reprint")
    @PreAuthorize("hasAuthority('SALES_INVOICE.REPRINT')")
    public SalesInvoiceDto reprint(@PathVariable @ValidUlid String uid,
                                   @Valid @RequestBody ReprintInvoiceRequestDto request) {
        return service.reprint(uid, request);
    }
}

package com.orbix.engine.api;

import com.orbix.engine.modules.cash.domain.dto.CashBookDto;
import com.orbix.engine.modules.cash.domain.dto.CashEntryDto;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.service.CashQueryService;
import com.orbix.engine.modules.common.validation.ValidUlid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Cash module read API (F6.1). Slice D — per-aggregate read permissions
 * {@code CASH.ENTRY.READ} / {@code CASH.BOOK.READ} alongside the existing
 * coarse {@code CASH.READ} (kept seeded as a group-grant for backwards
 * compatibility). Direct write endpoints live on the dedicated audit-doc
 * controllers; the ledger row itself is append-only and has no write surface
 * here (immutability invariant — DATA-MODEL.md §10.2).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CashLedgerController {

    private final CashQueryService service;

    @GetMapping("/cash-entries")
    @PreAuthorize("hasAnyAuthority('CASH.ENTRY.READ', 'CASH.READ')")
    public List<CashEntryDto> listEntries(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) CashAccount account,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return service.listEntries(branchId, account, businessDate);
    }

    @GetMapping("/cash-entries/uid/{uid}")
    @PreAuthorize("hasAnyAuthority('CASH.ENTRY.READ', 'CASH.READ')")
    public CashEntryDto getEntryByUid(@PathVariable @ValidUlid String uid) {
        return service.getCashEntryByUid(uid);
    }

    @GetMapping("/cash-book")
    @PreAuthorize("hasAnyAuthority('CASH.BOOK.READ', 'CASH.READ')")
    public List<CashBookDto> listCashBook(
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return service.listCashBook(branchId, businessDate);
    }

    @GetMapping("/cash-book/uid/{uid}")
    @PreAuthorize("hasAnyAuthority('CASH.BOOK.READ', 'CASH.READ')")
    public CashBookDto getCashBookByUid(@PathVariable @ValidUlid String uid) {
        return service.getCashBookByUid(uid);
    }
}

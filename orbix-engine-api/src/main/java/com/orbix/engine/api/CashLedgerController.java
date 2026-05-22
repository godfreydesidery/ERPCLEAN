package com.orbix.engine.api;

import com.orbix.engine.modules.cash.domain.dto.CashBookDto;
import com.orbix.engine.modules.cash.domain.dto.CashEntryDto;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.service.CashQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Cash module read API (F6.1). Gated by {@code CASH.READ}. Direct write
 * endpoints (supervisor adjustment, bank deposit) are tracked separately and
 * land in a follow-on slice once their underlying audit documents are modelled.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CASH.READ')")
public class CashLedgerController {

    private final CashQueryService service;

    @GetMapping("/cash-entries")
    public List<CashEntryDto> listEntries(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) CashAccount account,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return service.listEntries(branchId, account, businessDate);
    }

    @GetMapping("/cash-book")
    public List<CashBookDto> listCashBook(
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return service.listCashBook(branchId, businessDate);
    }
}

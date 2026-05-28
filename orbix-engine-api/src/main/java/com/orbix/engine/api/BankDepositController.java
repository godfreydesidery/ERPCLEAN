package com.orbix.engine.api;

import com.orbix.engine.modules.cash.domain.dto.BankDepositDto;
import com.orbix.engine.modules.cash.domain.dto.PostBankDepositRequestDto;
import com.orbix.engine.modules.cash.service.BankDepositService;
import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * End-of-day banking deposits (F6.3 / US-DAY-002). Slice D — granular
 * per-action permissions {@code CASH.BANK_DEPOSIT.POST} (write) and
 * {@code CASH.BANK_DEPOSIT.ARCHIVE} (reverse). Read endpoints accept the
 * granular post code or the legacy coarse {@code CASH.READ} /
 * {@code CASH.BANKING} for back-compat.
 */
@RestController
@RequestMapping("/api/v1/bank-deposits")
@RequiredArgsConstructor
public class BankDepositController {

    private final BankDepositService service;

    @PostMapping
    @PreAuthorize("hasAuthority('CASH.BANK_DEPOSIT.POST')")
    public ResponseEntity<BankDepositDto> post(@Valid @RequestBody PostBankDepositRequestDto request) {
        BankDepositDto saved = service.post(request);
        return ResponseEntity
            .created(URI.create("/api/v1/bank-deposits/uid/" + saved.uid()))
            .body(saved);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CASH.BANK_DEPOSIT.POST', 'CASH.BANKING', 'CASH.READ')")
    public List<BankDepositDto> list(
            @RequestParam Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return service.list(branchId, businessDate);
    }

    @GetMapping("/uid/{uid}")
    @PreAuthorize("hasAnyAuthority('CASH.BANK_DEPOSIT.POST', 'CASH.BANKING', 'CASH.READ')")
    public BankDepositDto getByUid(@PathVariable @ValidUlid String uid) {
        return service.getBankDepositByUid(uid);
    }

    @PostMapping("/uid/{uid}/archive")
    @PreAuthorize("hasAuthority('CASH.BANK_DEPOSIT.ARCHIVE')")
    public BankDepositDto archive(@PathVariable @ValidUlid String uid) {
        return service.archiveBankDepositByUid(uid);
    }
}

package com.orbix.engine.api;

import com.orbix.engine.modules.cash.domain.dto.BankDepositDto;
import com.orbix.engine.modules.cash.domain.dto.PostBankDepositRequestDto;
import com.orbix.engine.modules.cash.service.BankDepositService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/** End-of-day banking deposits (F6.3 / US-DAY-002). Gated by {@code CASH.BANKING}. */
@RestController
@RequestMapping("/api/v1/bank-deposits")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CASH.BANKING')")
public class BankDepositController {

    private final BankDepositService service;

    @PostMapping
    public ResponseEntity<BankDepositDto> post(@Valid @RequestBody PostBankDepositRequestDto request) {
        BankDepositDto saved = service.post(request);
        return ResponseEntity.created(URI.create("/api/v1/bank-deposits/" + saved.id())).body(saved);
    }

    @GetMapping
    public List<BankDepositDto> list(
            @RequestParam Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return service.list(branchId, businessDate);
    }
}

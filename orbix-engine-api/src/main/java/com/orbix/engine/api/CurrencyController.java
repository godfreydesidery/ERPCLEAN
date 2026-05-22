package com.orbix.engine.api;

import com.orbix.engine.modules.admin.domain.dto.CreateCurrencyRequestDto;
import com.orbix.engine.modules.admin.domain.dto.CurrencyDto;
import com.orbix.engine.modules.admin.service.CurrencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Admin currency management (F1.2). Gated by {@code ADMIN.MANAGE_CURRENCIES}. */
@RestController
@RequestMapping("/api/v1/currencies")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN.MANAGE_CURRENCIES')")
public class CurrencyController {

    private final CurrencyService service;

    /**
     * Open to any authenticated user — the currency catalog is a shared lookup
     * (e.g. the supplier form's default-currency picker needs it without
     * granting ADMIN.MANAGE_CURRENCIES). Writes below stay restricted.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CurrencyDto> listCurrencies() {
        return service.listCurrencies();
    }

    @PostMapping
    public ResponseEntity<CurrencyDto> createCurrency(
            @Valid @RequestBody CreateCurrencyRequestDto request) {
        CurrencyDto currency = service.createCurrency(request);
        return ResponseEntity.created(URI.create("/api/v1/currencies/" + currency.code())).body(currency);
    }

    @PostMapping("/{code}/enable")
    public CurrencyDto enableCurrency(@PathVariable String code) {
        return service.enableCurrency(code);
    }

    @PostMapping("/{code}/disable")
    public CurrencyDto disableCurrency(@PathVariable String code) {
        return service.disableCurrency(code);
    }
}

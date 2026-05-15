package com.orbix.engine.api;

import com.orbix.engine.modules.pos.domain.dto.TillCurrencyDto;
import com.orbix.engine.modules.pos.service.TillCurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Maintains the foreign currencies a till is allowed to accept as FX tender (F5.6).
 * Gated by {@code POS.TILL_CURRENCY_MANAGE}.
 */
@RestController
@RequestMapping("/api/v1/tills/{tillId}/currencies")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('POS.TILL_CURRENCY_MANAGE')")
public class TillCurrencyController {

    private final TillCurrencyService service;

    @GetMapping
    public List<TillCurrencyDto> list(@PathVariable Long tillId) {
        return service.list(tillId);
    }

    @PostMapping("/{currencyCode}")
    public ResponseEntity<TillCurrencyDto> add(@PathVariable Long tillId,
                                               @PathVariable String currencyCode) {
        TillCurrencyDto dto = service.add(tillId, currencyCode);
        return ResponseEntity.created(URI.create(
            "/api/v1/tills/" + tillId + "/currencies/" + dto.currencyCode())).body(dto);
    }

    @DeleteMapping("/{currencyCode}")
    public ResponseEntity<Void> remove(@PathVariable Long tillId,
                                       @PathVariable String currencyCode) {
        service.remove(tillId, currencyCode);
        return ResponseEntity.noContent().build();
    }
}

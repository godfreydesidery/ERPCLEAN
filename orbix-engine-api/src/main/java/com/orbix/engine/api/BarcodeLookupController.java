package com.orbix.engine.api;

import com.orbix.engine.modules.pos.domain.dto.ResolvedBarcodeDto;
import com.orbix.engine.modules.pos.service.BarcodeResolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Till-side barcode lookup (F5.8). The Flutter POS or the embedded scanner
 * SDK calls this when a scanned code is not in the local catalog snapshot
 * or when the till is online and prefers a server resolve. Decodes
 * scale-printed EAN-13 codes (leading {@code 2}) into item + weight.
 */
@RestController
@RequestMapping("/api/v1/pos")
@RequiredArgsConstructor
public class BarcodeLookupController {

    private final BarcodeResolverService service;

    @GetMapping("/barcode-lookup")
    @PreAuthorize("hasAuthority('POS.SALE_POST') or hasAuthority('POS.MANAGE_TILL')")
    public ResolvedBarcodeDto lookup(@RequestParam("code") String code) {
        return service.resolve(code);
    }
}

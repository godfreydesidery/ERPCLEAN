package com.orbix.engine.api;

import com.orbix.engine.modules.giftcard.domain.dto.GiftCardDto;
import com.orbix.engine.modules.giftcard.domain.dto.GiftCardTxnDto;
import com.orbix.engine.modules.giftcard.domain.dto.IssueGiftCardRequestDto;
import com.orbix.engine.modules.giftcard.domain.dto.RedeemGiftCardRequestDto;
import com.orbix.engine.modules.giftcard.domain.dto.RefundGiftCardRequestDto;
import com.orbix.engine.modules.giftcard.service.GiftCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Gift-card management (F7.1). Permissions split — issuance / lookup /
 * redemption / freezing each have their own role grant so a POS device
 * can hold {@code GIFTCARD.REDEEM} without being able to freeze cards.
 */
@RestController
@RequestMapping("/api/v1/gift-cards")
@RequiredArgsConstructor
public class GiftCardController {

    private final GiftCardService service;

    @PostMapping
    @PreAuthorize("hasAuthority('GIFTCARD.ISSUE')")
    public ResponseEntity<GiftCardDto> issue(@Valid @RequestBody IssueGiftCardRequestDto request) {
        GiftCardDto card = service.issue(request);
        return ResponseEntity.created(URI.create("/api/v1/gift-cards/" + card.code())).body(card);
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAuthority('GIFTCARD.LOOKUP') or hasAuthority('GIFTCARD.REDEEM')")
    public GiftCardDto lookup(@PathVariable String code) {
        return service.lookup(code);
    }

    @GetMapping("/{code}/transactions")
    @PreAuthorize("hasAuthority('GIFTCARD.LOOKUP') or hasAuthority('GIFTCARD.REDEEM')")
    public List<GiftCardTxnDto> transactions(@PathVariable String code) {
        return service.listTransactions(code);
    }

    @PostMapping("/{code}/redeem")
    @PreAuthorize("hasAuthority('GIFTCARD.REDEEM')")
    public GiftCardTxnDto redeem(@PathVariable String code,
                                 @Valid @RequestBody RedeemGiftCardRequestDto request) {
        return service.redeem(code, request);
    }

    @PostMapping("/{code}/refund")
    @PreAuthorize("hasAuthority('GIFTCARD.REDEEM')")
    public GiftCardTxnDto refund(@PathVariable String code,
                                 @Valid @RequestBody RefundGiftCardRequestDto request) {
        return service.refundCredit(code, request);
    }

    @PostMapping("/{code}/freeze")
    @PreAuthorize("hasAuthority('GIFTCARD.FREEZE')")
    public GiftCardDto freeze(@PathVariable String code) {
        return service.freeze(code);
    }

    @PostMapping("/{code}/unfreeze")
    @PreAuthorize("hasAuthority('GIFTCARD.FREEZE')")
    public GiftCardDto unfreeze(@PathVariable String code) {
        return service.unfreeze(code);
    }
}

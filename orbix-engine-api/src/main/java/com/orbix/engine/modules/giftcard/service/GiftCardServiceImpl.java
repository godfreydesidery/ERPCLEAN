package com.orbix.engine.modules.giftcard.service;

import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.CashRefType;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;
import com.orbix.engine.modules.cash.domain.enums.PaymentMethod;
import com.orbix.engine.modules.cash.service.CashLedgerService;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.giftcard.domain.dto.GiftCardDto;
import com.orbix.engine.modules.giftcard.domain.dto.GiftCardTxnDto;
import com.orbix.engine.modules.giftcard.domain.dto.IssueGiftCardRequestDto;
import com.orbix.engine.modules.giftcard.domain.dto.RedeemGiftCardRequestDto;
import com.orbix.engine.modules.giftcard.domain.dto.RefundGiftCardRequestDto;
import com.orbix.engine.modules.giftcard.domain.entity.GiftCard;
import com.orbix.engine.modules.giftcard.domain.entity.GiftCardTxn;
import com.orbix.engine.modules.giftcard.domain.enums.GiftCardStatus;
import com.orbix.engine.modules.giftcard.domain.enums.GiftCardTxnKind;
import com.orbix.engine.modules.giftcard.repository.GiftCardRepository;
import com.orbix.engine.modules.giftcard.repository.GiftCardTxnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GiftCardServiceImpl implements GiftCardService {

    private static final String AGG = "GiftCard";
    private static final SecureRandom RNG = new SecureRandom();
    private static final int CODE_DIGITS = 12;
    /** Safety net for collisions; the UNIQUE constraint is the source of truth. */
    private static final int CODE_GEN_ATTEMPTS = 8;

    private final GiftCardRepository cards;
    private final GiftCardTxnRepository txns;
    private final CompanyRepository companies;
    private final CashLedgerService cashLedger;
    private final DayGuard dayGuard;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional
    @Auditable(action = "ISSUE", entityType = AGG)
    public GiftCardDto issue(IssueGiftCardRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        BusinessDay day = dayGuard.requireOpenDay(request.branchId());
        String currency = requireCompanyCurrency(companyId);

        String code = resolveCode(request.code());
        GiftCard card = cards.save(new GiftCard(
            code, companyId, request.branchId(), actorId,
            request.initialValue(), currency, request.expiresAt(), actorId));

        Instant now = Instant.now();
        GiftCardTxn loadTxn = txns.save(new GiftCardTxn(
            card.getId(), GiftCardTxnKind.LOAD, request.initialValue(),
            card.getCurrentBalance(),
            CashRefType.GIFT_CARD_ISSUE, card.getId(),
            now, actorId));

        // Cash side of issuance — paid via the till. CARD settles off-ledger.
        Optional<CashAccount> cashAccount = accountFor(request.tenderMethod());
        if (cashAccount.isPresent()) {
            cashLedger.post(now, companyId, request.branchId(), day.getBusinessDate(),
                cashAccount.get(), CashDirection.IN, request.initialValue(),
                BigDecimal.ONE, currency,
                CashRefType.GIFT_CARD_ISSUE, card.getId(),
                GlCategory.GIFT_CARD_ISSUE_PROCEEDS,
                "GiftCard #" + maskCode(code), actorId);
        }

        events.publish("GiftCardIssued.v1", AGG, String.valueOf(card.getId()),
            Map.of("giftCardId", card.getId(),
                "branchId", card.getIssuedByBranchId(),
                "initialValue", card.getInitialValue(),
                "currencyCode", card.getCurrencyCode(),
                "loadTxnId", loadTxn.getId(),
                "expiresAt", card.getExpiresAt() == null ? "" : card.getExpiresAt().toString()));
        return GiftCardDto.from(card);
    }

    @Override
    @Transactional(readOnly = true)
    public GiftCardDto lookup(String code) {
        return GiftCardDto.from(requireCard(code));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GiftCardTxnDto> listTransactions(String code) {
        GiftCard card = requireCard(code);
        return txns.findByGiftCardIdOrderByOccurredAtAsc(card.getId()).stream()
            .map(GiftCardTxnDto::from)
            .toList();
    }

    @Override
    @Transactional
    @Auditable(action = "REDEEM", entityType = AGG)
    public GiftCardTxnDto redeem(String code, RedeemGiftCardRequestDto request) {
        GiftCard card = requireCard(code);
        Long actorId = context.userId();

        // Idempotency: same (card, refDocType, refDocId, REDEEM) returns prior txn.
        Optional<GiftCardTxn> existing = txns.findByGiftCardIdAndRefDocTypeAndRefDocIdAndKind(
            card.getId(), request.refDocType(), request.refDocId(), GiftCardTxnKind.REDEEM);
        if (existing.isPresent()) {
            return GiftCardTxnDto.from(existing.get());
        }

        card.debit(request.amount(), actorId);
        GiftCardTxn txn = txns.save(new GiftCardTxn(
            card.getId(), GiftCardTxnKind.REDEEM, request.amount(),
            card.getCurrentBalance(),
            request.refDocType(), request.refDocId(),
            Instant.now(), actorId));

        events.publish("GiftCardRedeemed.v1", AGG, String.valueOf(card.getId()),
            Map.of("giftCardId", card.getId(),
                "amount", txn.getAmount(),
                "balanceAfter", txn.getBalanceAfter(),
                "refDocType", txn.getRefDocType(),
                "refDocId", txn.getRefDocId(),
                "status", card.getStatus()));
        return GiftCardTxnDto.from(txn);
    }

    @Override
    @Transactional
    @Auditable(action = "REFUND_CREDIT", entityType = AGG)
    public GiftCardTxnDto refundCredit(String code, RefundGiftCardRequestDto request) {
        GiftCard card = requireCard(code);
        Long actorId = context.userId();

        Optional<GiftCardTxn> existing = txns.findByGiftCardIdAndRefDocTypeAndRefDocIdAndKind(
            card.getId(), request.refDocType(), request.refDocId(), GiftCardTxnKind.REFUND);
        if (existing.isPresent()) {
            return GiftCardTxnDto.from(existing.get());
        }

        card.credit(request.amount(), actorId);
        GiftCardTxn txn = txns.save(new GiftCardTxn(
            card.getId(), GiftCardTxnKind.REFUND, request.amount(),
            card.getCurrentBalance(),
            request.refDocType(), request.refDocId(),
            Instant.now(), actorId));

        events.publish("GiftCardRefunded.v1", AGG, String.valueOf(card.getId()),
            Map.of("giftCardId", card.getId(),
                "amount", txn.getAmount(),
                "balanceAfter", txn.getBalanceAfter(),
                "refDocType", txn.getRefDocType(),
                "refDocId", txn.getRefDocId(),
                "status", card.getStatus()));
        return GiftCardTxnDto.from(txn);
    }

    @Override
    @Transactional
    @Auditable(action = "FREEZE", entityType = AGG)
    public GiftCardDto freeze(String code) {
        GiftCard card = requireCard(code);
        card.freeze(context.userId());
        events.publish("GiftCardFrozen.v1", AGG, String.valueOf(card.getId()),
            Map.of("giftCardId", card.getId(), "balance", card.getCurrentBalance()));
        return GiftCardDto.from(card);
    }

    @Override
    @Transactional
    @Auditable(action = "UNFREEZE", entityType = AGG)
    public GiftCardDto unfreeze(String code) {
        GiftCard card = requireCard(code);
        card.unfreeze(context.userId());
        events.publish("GiftCardUnfrozen.v1", AGG, String.valueOf(card.getId()),
            Map.of("giftCardId", card.getId(), "status", card.getStatus()));
        return GiftCardDto.from(card);
    }

    @Override
    @Transactional
    public int runExpiryJob() {
        Instant now = Instant.now();
        Long systemActor = 0L;
        List<GiftCard> due = cards.findByStatusAndExpiresAtBefore(GiftCardStatus.ACTIVE, now);
        int expired = 0;
        for (GiftCard card : due) {
            BigDecimal forfeited = card.expire(systemActor);
            if (forfeited.signum() > 0) {
                txns.save(new GiftCardTxn(
                    card.getId(), GiftCardTxnKind.EXPIRE, forfeited,
                    BigDecimal.ZERO,
                    "GiftCardExpiryJob", card.getId(),
                    now, systemActor));
            }
            events.publish("GiftCardExpired.v1", AGG, String.valueOf(card.getId()),
                Map.of("giftCardId", card.getId(),
                    "forfeitedAmount", forfeited,
                    "expiresAt", card.getExpiresAt().toString()));
            expired++;
        }
        return expired;
    }

    private GiftCard requireCard(String code) {
        Long companyId = context.companyId();
        GiftCard card = cards.findByCode(code)
            .orElseThrow(() -> new NoSuchElementException("Gift card not found: " + maskCode(code)));
        if (!Objects.equals(card.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Gift card not found: " + maskCode(code));
        }
        return card;
    }

    private String resolveCode(String requested) {
        if (requested != null && !requested.isBlank()) {
            String trimmed = requested.trim();
            if (cards.existsByCode(trimmed)) {
                throw new IllegalArgumentException("Gift card code already in use");
            }
            return trimmed;
        }
        for (int i = 0; i < CODE_GEN_ATTEMPTS; i++) {
            String generated = generateCode();
            if (!cards.existsByCode(generated)) {
                return generated;
            }
        }
        throw new IllegalStateException("Failed to generate a unique gift card code");
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_DIGITS);
        for (int i = 0; i < CODE_DIGITS; i++) {
            sb.append(RNG.nextInt(10));
        }
        return sb.toString();
    }

    private static String maskCode(String code) {
        if (code == null || code.length() <= 4) {
            return "****";
        }
        return "****" + code.substring(code.length() - 4);
    }

    /** CARD tenders settle off-ledger (the card-rail processor). Mirrors SalesReceipt's map. */
    private static Optional<CashAccount> accountFor(PaymentMethod method) {
        return switch (method) {
            case CASH -> Optional.of(CashAccount.CASH_BOX);
            case MOBILE_MONEY -> Optional.of(CashAccount.MOBILE_MONEY);
            case BANK_TRANSFER, CHEQUE -> Optional.of(CashAccount.BANK);
        };
    }

    private String requireCompanyCurrency(Long companyId) {
        return companies.findById(companyId)
            .orElseThrow(() -> new NoSuchElementException("Company not found: " + companyId))
            .getCurrencyCode();
    }
}

package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.cash.domain.dto.CashEntryDto;
import com.orbix.engine.modules.cash.domain.entity.CashBook;
import com.orbix.engine.modules.cash.domain.entity.CashBookId;
import com.orbix.engine.modules.cash.domain.entity.CashEntry;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;
import com.orbix.engine.modules.cash.repository.CashBookRepository;
import com.orbix.engine.modules.cash.repository.CashEntryRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CashLedgerServiceImpl implements CashLedgerService {

    static final String AGG = "CashEntry";
    private static final int MONEY_SCALE = 4;

    private final CashEntryRepository entries;
    private final CashBookRepository books;
    private final EventPublisher events;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    @SuppressWarnings("java:S107")
    public CashEntryDto post(Instant at, Long companyId, Long branchId, LocalDate businessDate,
                             CashAccount account, CashDirection direction,
                             BigDecimal tenderAmount, BigDecimal fxRateSnapshot, String tenderCurrency,
                             String refType, Long refId, GlCategory glCategory, String notes, Long actorId) {
        Optional<CashEntry> existing = entries.findByRefTypeAndRefIdAndDirection(refType, refId, direction);
        if (existing.isPresent()) {
            return CashEntryDto.from(existing.get());
        }

        BigDecimal functionalAmount = tenderAmount.multiply(fxRateSnapshot)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        CashEntry saved = entries.save(new CashEntry(
            at, companyId, branchId, businessDate, account, direction,
            functionalAmount, tenderAmount, fxRateSnapshot, tenderCurrency,
            refType, refId, glCategory, notes, actorId
        ));
        applyToCashBook(saved);

        Map<String, Object> payload = new HashMap<>();
        payload.put("cashEntryId", saved.getId());
        payload.put("branchId", saved.getBranchId());
        payload.put("account", saved.getAccount());
        payload.put("direction", saved.getDirection());
        payload.put("amount", saved.getAmount());
        payload.put("tenderAmount", saved.getTenderAmount());
        payload.put("fxRateSnapshot", saved.getFxRateSnapshot());
        payload.put("currencyCode", saved.getCurrencyCode());
        payload.put("refType", saved.getRefType());
        payload.put("refId", saved.getRefId());
        payload.put("businessDate", saved.getBusinessDate());
        events.publish("CashEntryPosted.v1", AGG, String.valueOf(saved.getId()), payload);
        return CashEntryDto.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashEntryDto> findByRef(String refType, Long refId, CashDirection direction) {
        return entries.findByRefTypeAndRefIdAndDirection(refType, refId, direction)
            .map(CashEntryDto::from);
    }

    /** cash_book is partitioned per tender currency; amounts on the row stay in that currency. */
    private void applyToCashBook(CashEntry entry) {
        CashBookId id = new CashBookId(entry.getBranchId(), entry.getAccount(),
            entry.getCurrencyCode(), entry.getBusinessDate());
        CashBook book = books.findById(id)
            .orElseGet(() -> new CashBook(id, entry.getCompanyId(), BigDecimal.ZERO));
        if (entry.getDirection() == CashDirection.IN) {
            book.addIn(entry.getTenderAmount());
        } else {
            book.addOut(entry.getTenderAmount());
        }
        CashBook saved = books.save(book);

        events.publish("CashBookBalanceUpdated.v1", "CashBook",
            saved.getBranchId() + ":" + saved.getAccount() + ":" + saved.getCurrencyCode()
                + ":" + saved.getBusinessDate(),
            Map.of(
                "branchId", saved.getBranchId(),
                "account", saved.getAccount(),
                "currencyCode", saved.getCurrencyCode(),
                "businessDate", saved.getBusinessDate(),
                "openingAmount", saved.getOpeningAmount(),
                "inAmount", saved.getInAmount(),
                "outAmount", saved.getOutAmount(),
                "closingAmount", saved.getClosingAmount()));
    }
}

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CashLedgerServiceImpl implements CashLedgerService {

    static final String AGG = "CashEntry";

    private final CashEntryRepository entries;
    private final CashBookRepository books;
    private final EventPublisher events;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    @SuppressWarnings("java:S107")
    public CashEntryDto post(Instant at, Long companyId, Long branchId, LocalDate businessDate,
                             CashAccount account, CashDirection direction, BigDecimal amount,
                             String currencyCode, String refType, Long refId,
                             GlCategory glCategory, String notes, Long actorId) {
        Optional<CashEntry> existing = entries.findByRefTypeAndRefIdAndDirection(refType, refId, direction);
        if (existing.isPresent()) {
            return CashEntryDto.from(existing.get());
        }

        CashEntry saved = entries.save(new CashEntry(
            at, companyId, branchId, businessDate, account, direction, amount,
            currencyCode, refType, refId, glCategory, notes, actorId
        ));
        applyToCashBook(saved);

        events.publish("CashEntryPosted.v1", AGG, String.valueOf(saved.getId()),
            Map.of(
                "cashEntryId", saved.getId(),
                "branchId", saved.getBranchId(),
                "account", saved.getAccount(),
                "direction", saved.getDirection(),
                "amount", saved.getAmount(),
                "currencyCode", saved.getCurrencyCode(),
                "refType", saved.getRefType(),
                "refId", saved.getRefId(),
                "businessDate", saved.getBusinessDate()));
        return CashEntryDto.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashEntryDto> findByRef(String refType, Long refId, CashDirection direction) {
        return entries.findByRefTypeAndRefIdAndDirection(refType, refId, direction)
            .map(CashEntryDto::from);
    }

    private void applyToCashBook(CashEntry entry) {
        CashBookId id = new CashBookId(entry.getBranchId(), entry.getAccount(), entry.getBusinessDate());
        CashBook book = books.findById(id)
            .orElseGet(() -> new CashBook(id, entry.getCompanyId(), entry.getCurrencyCode(), BigDecimal.ZERO));
        if (entry.getDirection() == CashDirection.IN) {
            book.addIn(entry.getAmount());
        } else {
            book.addOut(entry.getAmount());
        }
        CashBook saved = books.save(book);

        events.publish("CashBookBalanceUpdated.v1", "CashBook",
            saved.getBranchId() + ":" + saved.getAccount() + ":" + saved.getBusinessDate(),
            Map.of(
                "branchId", saved.getBranchId(),
                "account", saved.getAccount(),
                "businessDate", saved.getBusinessDate(),
                "openingAmount", saved.getOpeningAmount(),
                "inAmount", saved.getInAmount(),
                "outAmount", saved.getOutAmount(),
                "closingAmount", saved.getClosingAmount()));
    }
}

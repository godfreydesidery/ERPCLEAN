package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.cash.domain.dto.CashEntryDto;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Cash module's posting port (F6.1). Source modules (pos, sales,
 * procurement, day) call into this in the same transaction as their own
 * write so a rolled-back source document also rolls back the cash entry.
 *
 * <p>Idempotency is enforced on the triple {@code (refType, refId, direction)} —
 * repeated calls for the same source-document key are a no-op. The projection
 * in {@code cash_book} is maintained write-through in the same transaction.
 */
public interface CashLedgerService {

    /**
     * Idempotent post. Returns the existing entry if the {@code (refType, refId, direction)}
     * triple has already been written, else inserts a new one. Maintains the
     * {@code cash_book} projection write-through.
     *
     * @throws IllegalArgumentException if {@code amount <= 0}
     */
    @SuppressWarnings("java:S107")  // posting row is inherently wide; a VO would only shuffle the args
    CashEntryDto post(Instant at,
                      Long companyId,
                      Long branchId,
                      LocalDate businessDate,
                      CashAccount account,
                      CashDirection direction,
                      BigDecimal amount,
                      String currencyCode,
                      String refType,
                      Long refId,
                      GlCategory glCategory,
                      String notes,
                      Long actorId);

    /** Read-side idempotency probe. */
    Optional<CashEntryDto> findByRef(String refType, Long refId, CashDirection direction);
}

package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.cash.domain.dto.CashBookDto;
import com.orbix.engine.modules.cash.domain.dto.CashEntryDto;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;

import java.time.LocalDate;
import java.util.List;

/** Read-side queries for the cash ledger + cash-book projection (F6.1). */
public interface CashQueryService {

    List<CashEntryDto> listEntries(Long branchId, CashAccount account, LocalDate businessDate);

    List<CashBookDto> listCashBook(Long branchId, LocalDate businessDate);
}

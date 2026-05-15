package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.cash.domain.dto.BankDepositDto;
import com.orbix.engine.modules.cash.domain.dto.PostBankDepositRequestDto;

import java.time.LocalDate;
import java.util.List;

/**
 * End-of-day banking deposits (F6.3 / TC-CASH-012). Each deposit writes
 * paired cash entries — OUT on CASH_BOX and IN on BANK — in the same
 * transaction as the {@code bank_deposit} audit row. The controller gates on
 * {@code CASH.BANKING}; the business day must be OPEN.
 */
public interface BankDepositService {

    BankDepositDto post(PostBankDepositRequestDto request);

    List<BankDepositDto> list(Long branchId, LocalDate businessDate);
}

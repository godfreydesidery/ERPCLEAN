package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.cash.domain.dto.BankDepositDto;
import com.orbix.engine.modules.cash.domain.dto.PostBankDepositRequestDto;

import java.time.LocalDate;
import java.util.List;

/**
 * End-of-day banking deposits (F6.3 / TC-CASH-012). Each deposit writes
 * paired cash entries — OUT on CASH_BOX and IN on BANK — in the same
 * transaction as the {@code bank_deposit} audit row. The controller gates
 * on {@code CASH.BANK_DEPOSIT.POST}; the business day must be OPEN.
 *
 * <p>Slice D — deposits carry a {@code uid} URL handle and a reversal
 * lifecycle: {@link #archiveBankDepositByUid(String)} posts the paired
 * compensating cash entries under {@code BANK_DEPOSIT_REVERSAL} and stamps
 * the audit-doc's reversal columns. Reversal is single-shot.
 */
public interface BankDepositService {

    BankDepositDto post(PostBankDepositRequestDto request);

    List<BankDepositDto> list(Long branchId, LocalDate businessDate);

    /** uid-keyed read; tenant-checked. */
    BankDepositDto getBankDepositByUid(String uid);

    /**
     * Reverse a posted bank deposit by uid. Throws if the row was already
     * reversed or belongs to a different tenant. Emits
     * {@code BankDepositReversed.v1}.
     */
    BankDepositDto archiveBankDepositByUid(String uid);
}

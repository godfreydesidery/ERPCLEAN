package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.pos.domain.dto.PettyCashDto;
import com.orbix.engine.modules.pos.domain.dto.PostPettyCashRequestDto;

import java.util.List;

/**
 * Petty-cash payouts from the till (F5.9 / US-POS-014). Posts a single
 * OUT-TILL cash entry with {@code gl_category = PETTY}; the payout leaves
 * the cash system entirely (no paired IN). The till session must be OPEN;
 * the business day must be OPEN; the authoriser must hold
 * {@code POS.PETTY_CASH} and differ from the caller.
 */
public interface PettyCashService {

    PettyCashDto post(PostPettyCashRequestDto request);

    List<PettyCashDto> listForSession(Long tillSessionId);

    /** uid-keyed read; tenant-checked. */
    PettyCashDto getPettyCashByUid(String uid);
}

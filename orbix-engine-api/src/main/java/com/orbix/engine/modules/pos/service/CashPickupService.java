package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.pos.domain.dto.CashPickupDto;
import com.orbix.engine.modules.pos.domain.dto.PostCashPickupRequestDto;

import java.util.List;

/**
 * Mid-shift cash pickups (F5.9 / US-POS-013). Each pickup posts paired cash
 * entries: OUT on the till and IN on the back-office cash box, in the
 * same transaction. The till session must be OPEN; the business day must be
 * OPEN; the authoriser must hold {@code POS.CASH_PICKUP} and be a different
 * user from the caller.
 */
public interface CashPickupService {

    CashPickupDto post(PostCashPickupRequestDto request);

    List<CashPickupDto> listForSession(Long tillSessionId);

    /** uid-keyed read; tenant-checked. */
    CashPickupDto getCashPickupByUid(String uid);
}

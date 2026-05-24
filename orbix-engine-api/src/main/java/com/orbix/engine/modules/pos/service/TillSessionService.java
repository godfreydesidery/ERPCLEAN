package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.pos.domain.dto.CloseTillSessionRequestDto;
import com.orbix.engine.modules.pos.domain.dto.OpenTillSessionRequestDto;
import com.orbix.engine.modules.pos.domain.dto.TillSessionDto;

import java.util.List;

/**
 * Till sessions (F5.1). OPEN → CLOSED → RECONCILED. At most one OPEN session
 * per till. Opening requires the till's branch business day to be OPEN.
 * Closing requires a declared-cash count and, when |variance| exceeds the
 * configured threshold, a supervisor holding
 * {@code POS.SESSION_VARIANCE_APPROVE}. POS-sale cash receipts are folded into
 * the expected-cash calculation as those flows ship (F5.2+); for F5.1 the
 * expected_cash is just {@code opening_float}.
 */
public interface TillSessionService {

    TillSessionDto open(OpenTillSessionRequestDto request);

    TillSessionDto close(String uid, CloseTillSessionRequestDto request);

    TillSessionDto reconcile(String uid);

    List<TillSessionDto> list(Long branchId);

    List<TillSessionDto> listByTill(Long tillId);

    TillSessionDto get(String uid);
}

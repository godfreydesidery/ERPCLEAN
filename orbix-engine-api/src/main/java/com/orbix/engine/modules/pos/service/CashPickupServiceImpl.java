package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.CashRefType;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;
import com.orbix.engine.modules.cash.service.CashLedgerService;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.pos.domain.dto.CashPickupDto;
import com.orbix.engine.modules.pos.domain.dto.PostCashPickupRequestDto;
import com.orbix.engine.modules.pos.domain.entity.CashPickup;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import com.orbix.engine.modules.pos.repository.CashPickupRepository;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CashPickupServiceImpl implements CashPickupService {

    private static final String AGG = "CashPickup";
    static final String PERMISSION = "POS.CASH_PICKUP";

    private final CashPickupRepository pickups;
    private final TillSessionRepository sessions;
    private final CompanyRepository companies;
    private final CashLedgerService cashLedger;
    private final DayGuard dayGuard;
    private final PermissionResolverService permissions;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG)
    public CashPickupDto post(PostCashPickupRequestDto request) {
        Long actorId = context.userId();
        Long companyId = context.companyId();
        TillSession session = requireOpenSession(request.tillSessionId(), companyId);
        validateAuthoriser(request.authorisedBy(), actorId, companyId);
        dayGuard.requireOpenDay(session.getBranchId());

        Instant at = Instant.now();
        CashPickup saved = pickups.save(new CashPickup(
            session.getId(), companyId, session.getBranchId(), session.getBusinessDate(),
            request.amount(), at, actorId, request.authorisedBy(), request.note()
        ));

        String currency = requireCompanyCurrency(companyId);
        // Paired entries: cash leaves the till, lands in the back-office cash box.
        // Same ref_id (the pickup), different direction → both pass the (ref_type, ref_id, direction)
        // idempotency UNIQUE constraint.
        cashLedger.post(at, companyId, session.getBranchId(), session.getBusinessDate(),
            CashAccount.TILL, CashDirection.OUT, request.amount(), currency,
            CashRefType.CASH_PICKUP, saved.getId(), GlCategory.CASH, request.note(), actorId);
        cashLedger.post(at, companyId, session.getBranchId(), session.getBusinessDate(),
            CashAccount.CASH_BOX, CashDirection.IN, request.amount(), currency,
            CashRefType.CASH_PICKUP, saved.getId(), GlCategory.CASH, request.note(), actorId);

        events.publish("CashPickupRecorded.v1", AGG, String.valueOf(saved.getId()),
            Map.of("cashPickupId", saved.getId(),
                "tillSessionId", session.getId(),
                "branchId", session.getBranchId(),
                "amount", saved.getAmount(),
                "businessDate", session.getBusinessDate()));
        return CashPickupDto.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashPickupDto> listForSession(Long tillSessionId) {
        Long companyId = context.companyId();
        TillSession session = sessions.findById(tillSessionId)
            .orElseThrow(() -> new NoSuchElementException("Till session not found: " + tillSessionId));
        if (!Objects.equals(session.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Till session not found: " + tillSessionId);
        }
        return pickups.findByTillSessionIdOrderByAtAsc(tillSessionId).stream()
            .map(CashPickupDto::from)
            .toList();
    }

    private TillSession requireOpenSession(Long sessionId, Long companyId) {
        TillSession session = sessions.findById(sessionId)
            .orElseThrow(() -> new NoSuchElementException("Till session not found: " + sessionId));
        if (!Objects.equals(session.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Till session not found: " + sessionId);
        }
        if (session.getStatus() != TillSessionStatus.OPEN) {
            throw new IllegalArgumentException(
                "Till session " + sessionId + " is " + session.getStatus() + " — cannot record a pickup");
        }
        return session;
    }

    private void validateAuthoriser(Long authorisedBy, Long actorId, Long companyId) {
        if (Objects.equals(authorisedBy, actorId)) {
            throw new IllegalArgumentException("You cannot authorise your own cash pickup");
        }
        boolean ok = permissions.resolve(authorisedBy, companyId, null).contains(PERMISSION);
        if (!ok) {
            throw new AccessDeniedException(
                "Authoriser " + authorisedBy + " does not hold " + PERMISSION);
        }
    }

    private String requireCompanyCurrency(Long companyId) {
        return companies.findById(companyId)
            .orElseThrow(() -> new NoSuchElementException("Company not found: " + companyId))
            .getCurrencyCode();
    }
}

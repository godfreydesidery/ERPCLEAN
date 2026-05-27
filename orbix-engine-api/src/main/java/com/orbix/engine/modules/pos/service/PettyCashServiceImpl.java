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
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.pos.domain.dto.PettyCashDto;
import com.orbix.engine.modules.pos.domain.dto.PostPettyCashRequestDto;
import com.orbix.engine.modules.pos.domain.entity.PettyCash;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import com.orbix.engine.modules.pos.repository.PettyCashRepository;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PettyCashServiceImpl implements PettyCashService {

    private static final String AGG = "PettyCash";
    static final String PERMISSION = "POS.PETTY_CASH";

    private final PettyCashRepository payouts;
    private final TillSessionRepository sessions;
    private final CompanyRepository companies;
    private final CashLedgerService cashLedger;
    private final DayGuard dayGuard;
    private final PermissionResolverService permissions;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG)
    public PettyCashDto post(PostPettyCashRequestDto request) {
        Long actorId = context.userId();
        Long companyId = context.companyId();
        TillSession session = requireOpenSession(request.tillSessionId(), companyId);
        validateAuthoriser(request.authorisedBy(), actorId, companyId);
        dayGuard.requireOpenDay(session.getBranchId());

        Instant at = Instant.now();
        PettyCash saved = payouts.save(new PettyCash(
            session.getId(), companyId, session.getBranchId(), session.getBusinessDate(),
            request.amount(), at, request.category(), request.paidTo(),
            actorId, request.authorisedBy(), request.description(), request.receiptAttachmentKey()
        ));

        // Single OUT-TILL — petty cash leaves the system; no paired IN.
        // Always functional currency; F6.2 stores fx_rate_snapshot = 1.
        cashLedger.post(at, companyId, session.getBranchId(), session.getBusinessDate(),
            CashAccount.TILL, CashDirection.OUT, request.amount(), BigDecimal.ONE,
            requireCompanyCurrency(companyId),
            CashRefType.PETTY_CASH, saved.getId(), GlCategory.PETTY,
            request.description(), actorId);

        events.publish("PettyCashPaid.v1", AGG, String.valueOf(saved.getId()),
            Map.of("pettyCashId", saved.getId(),
                "tillSessionId", session.getId(),
                "branchId", session.getBranchId(),
                "amount", saved.getAmount(),
                "category", saved.getCategory(),
                "businessDate", session.getBusinessDate()));
        return PettyCashDto.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PettyCashDto> listForSession(Long tillSessionId) {
        Long companyId = context.companyId();
        TillSession session = sessions.findById(tillSessionId)
            .orElseThrow(() -> new NoSuchElementException("Till session not found: " + tillSessionId));
        if (!Objects.equals(session.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Till session not found: " + tillSessionId);
        }
        branchScope.requireAccess(session.getBranchId());
        return payouts.findByTillSessionIdOrderByAtAsc(tillSessionId).stream()
            .map(PettyCashDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PettyCashDto getPettyCashByUid(String uid) {
        Long companyId = context.companyId();
        PettyCash payout = payouts.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Petty cash not found: " + uid));
        if (!Objects.equals(payout.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Petty cash not found: " + uid);
        }
        branchScope.requireAccess(payout.getBranchId());
        return PettyCashDto.from(payout);
    }

    private TillSession requireOpenSession(Long sessionId, Long companyId) {
        TillSession session = sessions.findById(sessionId)
            .orElseThrow(() -> new NoSuchElementException("Till session not found: " + sessionId));
        if (!Objects.equals(session.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Till session not found: " + sessionId);
        }
        branchScope.requireAccess(session.getBranchId());
        if (session.getStatus() != TillSessionStatus.OPEN) {
            throw new IllegalArgumentException(
                "Till session " + sessionId + " is " + session.getStatus() + " — cannot record petty cash");
        }
        return session;
    }

    private void validateAuthoriser(Long authorisedBy, Long actorId, Long companyId) {
        if (Objects.equals(authorisedBy, actorId)) {
            throw new IllegalArgumentException("You cannot authorise your own petty-cash payout");
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

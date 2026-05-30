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
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.pos.domain.dto.CloseTillSessionRequestDto;
import com.orbix.engine.modules.pos.domain.dto.OpenTillSessionRequestDto;
import com.orbix.engine.modules.pos.domain.dto.TillSessionBalanceDto;
import com.orbix.engine.modules.pos.domain.dto.TillSessionDto;
import com.orbix.engine.modules.pos.domain.entity.PosPayment;
import com.orbix.engine.modules.pos.domain.entity.PosSale;
import com.orbix.engine.modules.pos.domain.entity.Till;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.PosPaymentMethod;
import com.orbix.engine.modules.pos.domain.enums.PosSaleKind;
import com.orbix.engine.modules.pos.domain.enums.PosSaleStatus;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import com.orbix.engine.modules.pos.domain.enums.TillStatus;
import com.orbix.engine.modules.pos.repository.CashPickupRepository;
import com.orbix.engine.modules.pos.repository.PettyCashRepository;
import com.orbix.engine.modules.pos.repository.PosPaymentRepository;
import com.orbix.engine.modules.pos.repository.PosSaleRepository;
import com.orbix.engine.modules.pos.repository.TillRepository;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
import lombok.RequiredArgsConstructor;
import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.service.SettingsService;
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
public class TillSessionServiceImpl implements TillSessionService {

    static final String VARIANCE_APPROVE_PERMISSION = "POS.SESSION_VARIANCE_APPROVE";

    private static final String AGG = "TillSession";
    private static final String F_ID = "tillSessionId";
    private static final String F_TILL_ID = "tillId";
    private static final String F_BRANCH_ID = "branchId";

    private final TillSessionRepository sessions;
    private final TillRepository tills;
    private final PosSaleRepository sales;
    private final PosPaymentRepository payments;
    private final CashPickupRepository pickups;
    private final PettyCashRepository pettyCash;
    private final CompanyRepository companies;
    private final DayGuard dayGuard;
    private final CashLedgerService cashLedger;
    private final PermissionResolverService permissions;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;
    private final SettingsService settings;

    @Override
    @Transactional
    @Auditable(action = "OPEN", entityType = AGG)
    public TillSessionDto open(OpenTillSessionRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        Till till = requireTill(request.tillId());
        if (till.getStatus() != TillStatus.ACTIVE) {
            throw new IllegalArgumentException(
                "Till " + till.getCode() + " is " + till.getStatus() + " — cannot open a session");
        }
        if (sessions.findFirstByTillIdAndStatus(till.getId(), TillSessionStatus.OPEN).isPresent()) {
            throw new IllegalArgumentException(
                "Till " + till.getCode() + " already has an OPEN session");
        }
        BusinessDay day = dayGuard.requireOpenDay(till.getBranchId());

        TillSession session = sessions.save(new TillSession(
            till.getId(), till.getBranchId(), companyId,
            day.getBusinessDate(), actorId, request.openingFloatAmount()
        ));

        // F6.1: opening float — IN on TILL (the cash-box → till transfer is
        // recorded as a single IN since the cash box ledger row is owned by
        // the float assignment doc, not by the till session).
        if (request.openingFloatAmount().signum() > 0) {
            // Opening float is always functional currency; F6.2 stores fx_rate_snapshot = 1.
            cashLedger.post(
                Instant.now(),
                companyId,
                till.getBranchId(),
                day.getBusinessDate(),
                CashAccount.TILL,
                CashDirection.IN,
                request.openingFloatAmount(),
                BigDecimal.ONE,
                requireCompanyCurrency(companyId),
                CashRefType.TILL_FLOAT,
                session.getId(),
                GlCategory.TILL_FLOAT,
                null,
                actorId
            );
        }

        events.publish("TillSessionOpened.v1", AGG, String.valueOf(session.getId()),
            Map.of(F_ID, session.getId(),
                F_TILL_ID, till.getId(),
                F_BRANCH_ID, till.getBranchId(),
                "businessDate", session.getBusinessDate().toString(),
                "openingFloat", session.getOpeningFloatAmount()));
        return TillSessionDto.from(session);
    }

    private String requireCompanyCurrency(Long companyId) {
        return companies.findById(companyId)
            .orElseThrow(() -> new NoSuchElementException("Company not found: " + companyId))
            .getCurrencyCode();
    }

    @Override
    @Transactional
    @Auditable(action = "CLOSE", entityType = AGG)
    public TillSessionDto close(String uid, CloseTillSessionRequestDto request) {
        TillSession session = requireSessionByUid(uid);
        if (session.getStatus() != TillSessionStatus.OPEN) {
            throw new IllegalStateException(
                "Only OPEN sessions can be closed (was " + session.getStatus() + ")");
        }
        BigDecimal expectedCash = computeExpectedCash(session);
        BigDecimal variance = request.declaredCashAmount().subtract(expectedCash);
        Long actorId = context.userId();
        if (variance.abs().compareTo(settings.getDecimal(SettingKey.POS_SESSION_VARIANCE_THRESHOLD)) > 0) {
            validateSupervisor(request.supervisorId(), actorId);
        }
        session.close(expectedCash, request.declaredCashAmount(), actorId,
            request.supervisorId(), request.notes());

        // F6.1: variance entry on TILL. surplus (declared > expected) → IN,
        // shortage → OUT. Zero variance posts nothing. F6.2: per-currency
        // variance is a follow-on — for now the close request is a single
        // functional-currency declared total, so the variance lands in the
        // functional bucket with fx_rate_snapshot = 1.
        if (variance.signum() != 0) {
            cashLedger.post(
                Instant.now(),
                session.getCompanyId(),
                session.getBranchId(),
                session.getBusinessDate(),
                CashAccount.TILL,
                variance.signum() > 0 ? CashDirection.IN : CashDirection.OUT,
                variance.abs(),
                BigDecimal.ONE,
                requireCompanyCurrency(session.getCompanyId()),
                CashRefType.TILL_VARIANCE,
                session.getId(),
                GlCategory.VARIANCE,
                null,
                actorId
            );
        }

        events.publish("TillSessionClosed.v1", AGG, String.valueOf(session.getId()),
            Map.of(F_ID, session.getId(),
                F_TILL_ID, session.getTillId(),
                F_BRANCH_ID, session.getBranchId(),
                "expectedCash", expectedCash,
                "declaredCash", request.declaredCashAmount(),
                "variance", variance));
        return TillSessionDto.from(session);
    }

    @Override
    @Transactional
    @Auditable(action = "RECONCILE", entityType = AGG)
    public TillSessionDto reconcile(String uid) {
        TillSession session = requireSessionByUid(uid);
        session.reconcile(context.userId());
        events.publish("TillSessionReconciled.v1", AGG, String.valueOf(session.getId()),
            Map.of(F_ID, session.getId(),
                F_TILL_ID, session.getTillId()));
        return TillSessionDto.from(session);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TillSessionDto> list(Long branchId) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        List<TillSession> rows = scope == null
            ? sessions.findByCompanyIdOrderByIdDesc(companyId)
            : sessions.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, scope);
        return rows.stream().map(TillSessionDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TillSessionDto> listByTill(Long tillId) {
        requireTill(tillId);
        return sessions.findByTillIdOrderByIdDesc(tillId).stream().map(TillSessionDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TillSessionDto get(String uid) {
        return TillSessionDto.from(requireSessionByUid(uid));
    }

    @Override
    @Transactional(readOnly = true)
    public TillSessionBalanceDto getBalance(String uid) {
        TillSession session = requireSessionByUid(uid);
        BalanceBreakdown bd = computeBreakdown(session);
        return new TillSessionBalanceDto(
            session.getId(),
            session.getUid(),
            session.getOpeningFloatAmount(),
            bd.cashSales(),
            bd.cashPickups(),
            bd.pettyCash(),
            bd.expectedCash()
        );
    }

    /**
     * Expected drawer cash = opening_float + cash sales − cash refunds
     *                       − cash pickups (moved to safe) − petty-cash payouts.
     * Voided sales contribute 0 — the original cash IN is in cash_book but the
     * cashier handed the money back, so the drawer is unchanged. POSTED refunds
     * contribute a negative because the cashier paid out cash.
     */
    private BigDecimal computeExpectedCash(TillSession session) {
        return computeBreakdown(session).expectedCash();
    }

    /**
     * Breakdown of the cash balance for a session.
     * {@code cashSales} is the net signed contribution from sales and refunds
     * (positive = net receipts, negative = net payouts).
     */
    private BalanceBreakdown computeBreakdown(TillSession session) {
        BigDecimal cashSalesNet = BigDecimal.ZERO;
        for (PosSale sale : sales.findByTillSessionIdOrderByIdAsc(session.getId())) {
            cashSalesNet = cashSalesNet.add(cashContributionFor(sale));
        }
        BigDecimal cashPickupsTotal = pickups.sumForSession(session.getId());
        BigDecimal pettyCashTotal = pettyCash.sumForSession(session.getId());
        BigDecimal expectedCash = session.getOpeningFloatAmount()
            .add(cashSalesNet)
            .subtract(cashPickupsTotal)
            .subtract(pettyCashTotal);
        return new BalanceBreakdown(cashSalesNet, cashPickupsTotal, pettyCashTotal, expectedCash);
    }

    private record BalanceBreakdown(
        BigDecimal cashSales,
        BigDecimal cashPickups,
        BigDecimal pettyCash,
        BigDecimal expectedCash
    ) {}

    /**
     * Signed cash contribution of a single sale to the drawer:
     * POSTED + SALE   → +cash payments, POSTED + REFUND → -cash payments,
     * VOIDED → 0 (cashier already handed the cash back).
     */
    private BigDecimal cashContributionFor(PosSale sale) {
        if (sale.getStatus() != PosSaleStatus.POSTED) {
            return BigDecimal.ZERO;
        }
        BigDecimal cash = sumCashFunctional(sale.getId());
        return sale.getKind() == PosSaleKind.REFUND ? cash.negate() : cash;
    }

    private BigDecimal sumCashFunctional(Long saleId) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PosPayment p : payments.findByPosSaleIdOrderByIdAsc(saleId)) {
            if (p.getMethod() == PosPaymentMethod.CASH) {
                sum = sum.add(p.getAmount());
            }
        }
        return sum;
    }

    private void validateSupervisor(Long supervisorId, Long actorId) {
        if (supervisorId == null) {
            throw new IllegalArgumentException(
                "Variance above threshold requires a supervisor holding "
                    + VARIANCE_APPROVE_PERMISSION);
        }
        if (Objects.equals(supervisorId, actorId)) {
            throw new IllegalArgumentException("You cannot authorise your own variance");
        }
        boolean ok = permissions.resolve(supervisorId, context.companyId(), null)
            .contains(VARIANCE_APPROVE_PERMISSION);
        if (!ok) {
            throw new AccessDeniedException(
                "Supervisor " + supervisorId + " does not hold " + VARIANCE_APPROVE_PERMISSION);
        }
    }

    private TillSession requireSessionByUid(String uid) {
        TillSession session = sessions.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Till session not found: " + uid));
        if (!Objects.equals(session.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Till session not found: " + uid);
        }
        branchScope.requireAccess(session.getBranchId());
        return session;
    }

    private Till requireTill(Long tillId) {
        Till till = tills.findById(tillId)
            .orElseThrow(() -> new NoSuchElementException("Till not found: " + tillId));
        if (!Objects.equals(till.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Till not found: " + tillId);
        }
        branchScope.requireAccess(till.getBranchId());
        return till;
    }
}

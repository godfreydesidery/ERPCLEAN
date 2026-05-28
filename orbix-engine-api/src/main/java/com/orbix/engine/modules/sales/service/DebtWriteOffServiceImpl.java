package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.domain.enums.Permissions;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.repository.PartyRepository;
import com.orbix.engine.modules.procurement.domain.dto.SupplierInvoiceDto;
import com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus;
import com.orbix.engine.modules.procurement.service.SupplierInvoiceService;
import com.orbix.engine.modules.sales.domain.dto.CreateDebtWriteOffRequestDto;
import com.orbix.engine.modules.sales.domain.dto.DebtWriteOffDto;
import com.orbix.engine.modules.sales.domain.dto.RejectDebtWriteOffRequestDto;
import com.orbix.engine.modules.sales.domain.dto.SalesInvoiceDto;
import com.orbix.engine.modules.sales.domain.entity.DebtWriteOff;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffStatus;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffTargetKind;
import com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus;
import com.orbix.engine.modules.sales.domain.event.DebtWriteOffPosted;
import com.orbix.engine.modules.sales.domain.event.DebtWriteOffRejected;
import com.orbix.engine.modules.sales.domain.event.DebtWriteOffRequested;
import com.orbix.engine.modules.sales.repository.DebtWriteOffRepository;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Slice G.2 — debt write-off (AR + AP, dual approval).
 *
 * <p>ADR-0004 sync-TX exemption #20: when a write-off is posted (either on
 * the auto-post path in {@link #create} or in {@link #approve}), the call to
 * {@link SalesInvoiceService#applyWriteOff} or
 * {@link SupplierInvoiceService#applyWriteOff} happens in the same DB tx.
 * The invariant "if a write-off is POSTED, the invoice's paidAmount reflects
 * it" must hold strictly — eventual consistency would break aging queries
 * during the poll window.
 */
@Service
@RequiredArgsConstructor
public class DebtWriteOffServiceImpl implements DebtWriteOffService {

    private static final String AGG = "DebtWriteOff";

    private final DebtWriteOffRepository writeOffs;
    private final SalesInvoiceRepository salesInvoices;
    private final SalesInvoiceService salesInvoiceService;
    private final SupplierInvoiceService supplierInvoiceService;
    private final PartyRepository parties;
    private final AppUserRepository users;
    private final EventPublisher events;
    private final RequestContext context;

    @Value("${orbix.debt.write-off.dual-approval-threshold}")
    private BigDecimal dualApprovalThreshold;

    @Override
    @Transactional
    public DebtWriteOffDto create(CreateDebtWriteOffRequestDto req) {
        Long companyId = context.companyId();
        Long branchId = context.requireBranchId();
        Long requesterId = context.userId();
        Instant now = Instant.now();

        // 1. Resolve target invoice, validate status + amount.
        InvoiceInfo info = resolveInvoice(req, companyId);

        // 2. Determine auto-post vs. pending-approval path.
        boolean callerHasApprove = callerHasAuthority(Permissions.DEBT_WRITE_OFF_APPROVE);
        boolean autoPost = req.amount().compareTo(dualApprovalThreshold) <= 0 && callerHasApprove;

        DebtWriteOffStatus initialStatus = autoPost
            ? DebtWriteOffStatus.POSTED
            : DebtWriteOffStatus.PENDING_APPROVAL;

        DebtWriteOff wo = DebtWriteOff.builder()
            .companyId(companyId)
            .branchId(branchId)
            .targetKind(req.targetKind())
            .targetInvoiceId(info.invoiceId())
            .targetInvoiceUid(req.targetInvoiceUid())
            .amount(req.amount())
            .currencyCode(info.currencyCode())
            .reason(req.reason())
            .status(initialStatus)
            .requestedByUserId(requesterId)
            .requestedAt(now)
            .approvedByUserId(autoPost ? requesterId : null)
            .approvedAt(autoPost ? now : null)
            .postedAt(autoPost ? now : null)
            .createdAt(now)
            .updatedAt(now)
            .build();

        wo = writeOffs.save(wo);

        // 3. If auto-posted, apply write-off to invoice in same tx (ADR-0004 #20).
        if (autoPost) {
            applyToInvoice(req.targetKind(), info.invoiceId(), req.amount());
            events.publish(DebtWriteOffPosted.TYPE, AGG, String.valueOf(wo.getId()),
                new DebtWriteOffPosted(wo.getUid(), req.targetKind().name(), info.invoiceId(),
                    req.amount(), requesterId, requesterId, req.reason()).toPayload());
        } else {
            events.publish(DebtWriteOffRequested.TYPE, AGG, String.valueOf(wo.getId()),
                new DebtWriteOffRequested(wo.getUid(), req.targetKind().name(), info.invoiceId(),
                    req.amount(), requesterId, req.reason()).toPayload());
        }

        return toDto(wo, info.invoiceNumber(), info.partyName());
    }

    @Override
    @Transactional
    public DebtWriteOffDto approve(String uid) {
        DebtWriteOff wo = requireByUid(uid);
        Long approverId = context.userId();

        if (wo.getStatus() != DebtWriteOffStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                "Write-off is " + wo.getStatus() + ", expected PENDING_APPROVAL");
        }
        // Dual-control: above threshold the approver must differ from the requester.
        if (wo.getAmount().compareTo(dualApprovalThreshold) > 0
                && Objects.equals(approverId, wo.getRequestedByUserId())) {
            throw new IllegalStateException(
                "Amount exceeds dual-approval threshold ("
                    + dualApprovalThreshold + "): approver must differ from requester");
        }

        wo.approve(approverId);
        applyToInvoice(wo.getTargetKind(), wo.getTargetInvoiceId(), wo.getAmount());

        events.publish(DebtWriteOffPosted.TYPE, AGG, String.valueOf(wo.getId()),
            new DebtWriteOffPosted(wo.getUid(), wo.getTargetKind().name(), wo.getTargetInvoiceId(),
                wo.getAmount(), wo.getRequestedByUserId(), approverId, wo.getReason()).toPayload());

        return hydrate(wo);
    }

    @Override
    @Transactional
    public DebtWriteOffDto reject(String uid, RejectDebtWriteOffRequestDto req) {
        DebtWriteOff wo = requireByUid(uid);
        Long rejectorId = context.userId();

        if (wo.getStatus() != DebtWriteOffStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                "Write-off is " + wo.getStatus() + ", expected PENDING_APPROVAL");
        }
        // Rejection is always dual-control (regardless of amount).
        if (Objects.equals(rejectorId, wo.getRequestedByUserId())) {
            throw new IllegalStateException(
                "Rejection requires a different user from the requester (dual-control)");
        }

        wo.reject(rejectorId, req.reasonForReject());

        events.publish(DebtWriteOffRejected.TYPE, AGG, String.valueOf(wo.getId()),
            new DebtWriteOffRejected(wo.getUid(), wo.getTargetKind().name(), wo.getTargetInvoiceId(),
                wo.getAmount(), wo.getRequestedByUserId(), rejectorId, req.reasonForReject()).toPayload());

        return hydrate(wo);
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<DebtWriteOffDto> list(DebtWriteOffStatus status, DebtWriteOffTargetKind kind,
                                         Pageable pageable) {
        return PageDto.of(
            writeOffs.findFiltered(context.companyId(), status, kind, pageable),
            this::hydrate);
    }

    @Override
    @Transactional(readOnly = true)
    public DebtWriteOffDto get(String uid) {
        return hydrate(requireByUid(uid));
    }

    // ----- private helpers --------------------------------------------------

    private DebtWriteOff requireByUid(String uid) {
        DebtWriteOff wo = writeOffs.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Write-off not found: " + uid));
        if (!Objects.equals(wo.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Write-off not found: " + uid);
        }
        return wo;
    }

    private InvoiceInfo resolveInvoice(CreateDebtWriteOffRequestDto req, Long companyId) {
        if (req.targetKind() == DebtWriteOffTargetKind.CUSTOMER_INVOICE) {
            return resolveArInvoice(req, companyId);
        }
        return resolveApInvoice(req, companyId);
    }

    private InvoiceInfo resolveArInvoice(CreateDebtWriteOffRequestDto req, Long companyId) {
        SalesInvoiceDto inv = salesInvoiceService.get(req.targetInvoiceUid());
        if (!Objects.equals(inv.companyId(), companyId)) {
            throw new NoSuchElementException("Sales invoice not found: " + req.targetInvoiceUid());
        }
        validateArStatus(inv.status());
        validateAmount(req.amount(), inv.totalAmount().subtract(inv.paidAmount()), inv.number());
        return new InvoiceInfo(inv.id(), inv.number(), inv.currencyCode(), resolvePartyName(inv.customerId()));
    }

    private InvoiceInfo resolveApInvoice(CreateDebtWriteOffRequestDto req, Long companyId) {
        SupplierInvoiceDto inv = supplierInvoiceService.get(req.targetInvoiceUid());
        if (!Objects.equals(inv.companyId(), companyId)) {
            throw new NoSuchElementException("Supplier invoice not found: " + req.targetInvoiceUid());
        }
        validateApStatus(inv.status());
        validateAmount(req.amount(), inv.totalAmount().subtract(inv.paidAmount()), inv.number());
        return new InvoiceInfo(inv.id(), inv.number(), inv.currencyCode(), resolvePartyName(inv.supplierId()));
    }

    private void validateArStatus(SalesInvoiceStatus status) {
        if (status != SalesInvoiceStatus.POSTED && status != SalesInvoiceStatus.PARTIALLY_PAID) {
            throw new IllegalArgumentException(
                "Write-off only allowed on POSTED or PARTIALLY_PAID invoices (was " + status + ")");
        }
    }

    private void validateApStatus(SupplierInvoiceStatus status) {
        if (status != SupplierInvoiceStatus.POSTED && status != SupplierInvoiceStatus.PARTIALLY_PAID) {
            throw new IllegalArgumentException(
                "Write-off only allowed on POSTED or PARTIALLY_PAID invoices (was " + status + ")");
        }
    }

    private void validateAmount(BigDecimal amount, BigDecimal outstanding, String invoiceNumber) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Write-off amount must be positive: " + amount);
        }
        if (amount.compareTo(outstanding) > 0) {
            throw new IllegalArgumentException(
                "Write-off amount " + amount + " exceeds outstanding " + outstanding
                    + " on invoice " + invoiceNumber);
        }
    }

    private void applyToInvoice(DebtWriteOffTargetKind kind, Long invoiceId, BigDecimal amount) {
        if (kind == DebtWriteOffTargetKind.CUSTOMER_INVOICE) {
            salesInvoiceService.applyWriteOff(invoiceId, amount);
        } else {
            supplierInvoiceService.applyWriteOff(invoiceId, amount);
        }
    }

    /** Hydrate by loading invoice number + party name from repositories. */
    private DebtWriteOffDto hydrate(DebtWriteOff wo) {
        if (wo.getTargetKind() == DebtWriteOffTargetKind.CUSTOMER_INVOICE) {
            return hydrateAr(wo);
        }
        return hydrateAp(wo);
    }

    private DebtWriteOffDto hydrateAr(DebtWriteOff wo) {
        SalesInvoice inv = salesInvoices.findById(wo.getTargetInvoiceId()).orElse(null);
        String invoiceNumber = inv != null ? inv.getNumber() : null;
        String partyName = inv != null ? resolvePartyName(inv.getCustomerId()) : null;
        return toDto(wo, invoiceNumber, partyName);
    }

    private DebtWriteOffDto hydrateAp(DebtWriteOff wo) {
        SupplierInvoiceDto inv = fetchApInvoiceSafely(wo.getTargetInvoiceUid());
        String invoiceNumber = inv != null ? inv.number() : null;
        String partyName = inv != null ? resolvePartyName(inv.supplierId()) : null;
        return toDto(wo, invoiceNumber, partyName);
    }

    private SupplierInvoiceDto fetchApInvoiceSafely(String uid) {
        try {
            return supplierInvoiceService.get(uid);
        } catch (NoSuchElementException ignored) {
            return null;
        }
    }

    private String resolvePartyName(Long partyId) {
        return parties.findById(partyId).map(Party::getName).orElse(null);
    }

    private String resolveUsername(Long userId) {
        if (userId == null) return null;
        return users.findById(userId).map(AppUser::getUsername).orElse(null);
    }

    private DebtWriteOffDto toDto(DebtWriteOff wo, String invoiceNumber, String partyName) {
        return new DebtWriteOffDto(
            wo.getId(),
            wo.getUid(),
            wo.getTargetKind(),
            wo.getTargetInvoiceId(),
            wo.getTargetInvoiceUid(),
            invoiceNumber,
            partyName,
            wo.getAmount(),
            wo.getCurrencyCode(),
            wo.getReason(),
            wo.getStatus(),
            wo.getRequestedByUserId(),
            resolveUsername(wo.getRequestedByUserId()),
            wo.getRequestedAt(),
            wo.getApprovedByUserId(),
            resolveUsername(wo.getApprovedByUserId()),
            wo.getApprovedAt(),
            wo.getPostedAt(),
            wo.getRejectedAt(),
            wo.getReasonForReject()
        );
    }

    private boolean callerHasAuthority(String authority) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
            .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    /** Lightweight projection of resolved invoice metadata. */
    private record InvoiceInfo(Long invoiceId, String invoiceNumber, String currencyCode, String partyName) {}
}

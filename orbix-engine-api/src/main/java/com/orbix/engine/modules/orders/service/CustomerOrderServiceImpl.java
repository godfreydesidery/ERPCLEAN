package com.orbix.engine.modules.orders.service;

import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.cash.domain.dto.CashEntryDto;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.CashRefType;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;
import com.orbix.engine.modules.cash.service.CashLedgerService;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.giftcard.domain.dto.GiftCardTxnDto;
import com.orbix.engine.modules.giftcard.domain.dto.RedeemGiftCardRequestDto;
import com.orbix.engine.modules.giftcard.domain.dto.RefundGiftCardRequestDto;
import com.orbix.engine.modules.giftcard.service.GiftCardService;
import com.orbix.engine.modules.orders.domain.dto.CancelCustomerOrderRequestDto;
import com.orbix.engine.modules.orders.domain.dto.CreateCustomerOrderRequestDto;
import com.orbix.engine.modules.orders.domain.dto.CustomerOrderDto;
import com.orbix.engine.modules.orders.domain.dto.PatchCustomerOrderRequestDto;
import com.orbix.engine.modules.orders.domain.dto.PayCustomerOrderRequestDto;
import com.orbix.engine.modules.orders.domain.entity.CustomerOrder;
import com.orbix.engine.modules.orders.domain.entity.CustomerOrderLine;
import com.orbix.engine.modules.orders.domain.entity.CustomerOrderPayment;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderStatus;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderType;
import com.orbix.engine.modules.orders.domain.enums.OrderPaymentDirection;
import com.orbix.engine.modules.orders.domain.enums.OrderPaymentMethod;
import com.orbix.engine.modules.orders.repository.CustomerOrderLineRepository;
import com.orbix.engine.modules.orders.repository.CustomerOrderPaymentRepository;
import com.orbix.engine.modules.orders.repository.CustomerOrderRepository;
import com.orbix.engine.modules.party.repository.CustomerRepository;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.service.StockMoveService;
import com.orbix.engine.modules.stock.service.StockReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerOrderServiceImpl implements CustomerOrderService {

    private static final int MONEY_SCALE = 4;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String AGG = "CustomerOrder";
    private static final String F_ID = "customerOrderId";
    private static final String F_NUMBER = "number";

    private final CustomerOrderRepository orders;
    private final CustomerOrderLineRepository lines;
    private final CustomerOrderPaymentRepository payments;
    private final ItemRepository items;
    private final CustomerRepository customers;
    private final CompanyRepository companies;
    private final CashLedgerService cashLedger;
    private final GiftCardService giftCards;
    private final StockReservationService reservations;
    private final StockMoveService stockMoves;
    private final DayGuard dayGuard;
    private final EventPublisher events;
    private final RequestContext context;

    @Value("${orbix.orders.deposit-required-pct:30}")
    private BigDecimal depositRequiredPct;

    @Value("${orbix.orders.default-layby-reserve-days:30}")
    private int defaultLaybyReserveDays;

    @Value("${orbix.orders.default-pre-order-reserve-days:7}")
    private int defaultPreOrderReserveDays;

    @Value("${orbix.orders.cancel-refund-window-days:7}")
    private int cancelRefundWindowDays;

    // ---------------------------------------------------------------------
    // Create / patch
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = AGG)
    public CustomerOrderDto create(CreateCustomerOrderRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        dayGuard.requireOpenDay(request.branchId());
        requireCustomer(request.customerId());
        validateLines(request.lines(), companyId);

        String currency = requireCompanyCurrency(companyId);
        String number = resolveNumber(request.number(), request.branchId());
        Instant reservedUntil = request.reservedUntil() != null
            ? request.reservedUntil()
            : defaultReservedUntil(request.type());

        CustomerOrder order = orders.save(new CustomerOrder(
            number, companyId, request.branchId(), request.sectionId(),
            request.customerId(), request.type(), currency,
            reservedUntil, BigDecimal.ZERO,
            request.notes(), actorId));

        List<CustomerOrderLine> savedLines = saveLinesAndRollUp(order, request.lines());

        BigDecimal depositRequired = request.depositRequiredAmount() != null
            ? request.depositRequiredAmount()
            : defaultDeposit(order.getTotalAmount());
        order.setDepositRequiredAmount(depositRequired);

        events.publish(eventTypeForCreate(order.getType()), AGG, String.valueOf(order.getId()),
            Map.of(F_ID, order.getId(), F_NUMBER, order.getNumber(),
                "type", order.getType().name(),
                "customerId", order.getCustomerId(),
                "branchId", order.getBranchId(),
                "totalAmount", order.getTotalAmount(),
                "depositRequiredAmount", order.getDepositRequiredAmount()));
        return CustomerOrderDto.from(order, savedLines, List.of());
    }

    @Override
    @Transactional
    @Auditable(action = "PATCH", entityType = AGG)
    public CustomerOrderDto patch(Long orderId, PatchCustomerOrderRequestDto request) {
        CustomerOrder order = requireOrder(orderId);
        if (order.getStatus() != CustomerOrderStatus.DRAFT) {
            throw new IllegalStateException(
                "Order lines can only be edited while DRAFT (status was " + order.getStatus() + ")");
        }
        validateLines(request.lines(), order.getCompanyId());

        lines.deleteByCustomerOrderId(order.getId());
        List<CustomerOrderLine> savedLines = saveLinesAndRollUp(order, request.lines());
        if (request.reservedUntil() != null) {
            order.setReservedUntil(request.reservedUntil());
        }
        if (request.notes() != null) {
            order.setNotes(request.notes());
        }
        order.setUpdatedAt(Instant.now());
        order.setUpdatedBy(context.userId());

        events.publish("CustomerOrderPatched.v1", AGG, String.valueOf(order.getId()),
            Map.of(F_ID, order.getId(), F_NUMBER, order.getNumber(),
                "totalAmount", order.getTotalAmount()));
        return CustomerOrderDto.from(order, savedLines, paymentsFor(order.getId()));
    }

    // ---------------------------------------------------------------------
    // Reserve
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "RESERVE", entityType = AGG)
    public CustomerOrderDto reserve(Long orderId) {
        CustomerOrder order = requireOrder(orderId);
        if (order.getStatus() != CustomerOrderStatus.DRAFT) {
            throw new IllegalStateException(
                "Only DRAFT orders can be reserved (was " + order.getStatus() + ")");
        }
        dayGuard.requireOpenDay(order.getBranchId());

        if (order.getType() == CustomerOrderType.LAYBY) {
            for (CustomerOrderLine line : linesFor(order.getId())) {
                reservations.reserve(line.getItemId(), order.getBranchId(), line.getQty(),
                    AGG, order.getId(), "Order " + order.getNumber());
            }
        }
        order.markReserved(context.userId());

        events.publish("CustomerOrderReserved.v1", AGG, String.valueOf(order.getId()),
            Map.of(F_ID, order.getId(), F_NUMBER, order.getNumber(),
                "type", order.getType().name(),
                "branchId", order.getBranchId()));
        return loadDto(order);
    }

    // ---------------------------------------------------------------------
    // Pay
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "PAY", entityType = AGG)
    public CustomerOrderDto pay(Long orderId, PayCustomerOrderRequestDto request) {
        CustomerOrder order = requireOrder(orderId);
        Long actorId = context.userId();

        // Idempotency probe — same (order, idempotencyKey) returns the prior state.
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<CustomerOrderPayment> prior = payments
                .findByCustomerOrderIdAndIdempotencyKey(order.getId(), request.idempotencyKey());
            if (prior.isPresent()) {
                return loadDto(order);
            }
        }

        if (request.method() == OrderPaymentMethod.GIFT_CARD
                && (request.reference() == null || request.reference().isBlank())) {
            throw new IllegalArgumentException(
                "GIFT_CARD payment requires the card code in `reference`");
        }

        BusinessDay day = dayGuard.requireOpenDay(order.getBranchId());
        boolean countsTowardDeposit = order.getDepositPaidAmount()
            .compareTo(order.getDepositRequiredAmount()) < 0;

        // Persist the payment row first so its id can drive the downstream
        // idempotency keys for cash entries / gift-card txns.
        Instant now = Instant.now();
        CustomerOrderPayment payment = payments.save(new CustomerOrderPayment(
            order.getId(), request.amount(), request.method(),
            OrderPaymentDirection.IN, request.reference(), request.notes(),
            now, actorId, request.idempotencyKey()));

        switch (request.method()) {
            case GIFT_CARD -> {
                GiftCardTxnDto txn = giftCards.redeem(request.reference().trim(),
                    new RedeemGiftCardRequestDto(request.amount(), AGG + "Payment", payment.getId()));
                payment.setRefGiftcardTxnId(txn.id());
            }
            case CASH, BANK_TRANSFER, MOBILE_MONEY, CHEQUE -> {
                CashAccount account = accountForCash(request.method());
                CashEntryDto entry = cashLedger.post(now, order.getCompanyId(), order.getBranchId(),
                    day.getBusinessDate(), account, CashDirection.IN,
                    request.amount(), BigDecimal.ONE, order.getCurrencyCode(),
                    CashRefType.ORDER_PAYMENT, payment.getId(),
                    GlCategory.ORDER_DEPOSIT,
                    "Order " + order.getNumber(), actorId);
                payment.setRefCashEntryId(entry.id());
            }
            case CARD -> {
                /* CARD settles off-ledger — no cash entry. */
            }
        }

        order.applyPayment(request.amount(), countsTowardDeposit, actorId);

        events.publish(eventTypeForPayment(order, countsTowardDeposit), AGG, String.valueOf(order.getId()),
            Map.of(F_ID, order.getId(), F_NUMBER, order.getNumber(),
                "paymentId", payment.getId(),
                "amount", payment.getAmount(),
                "method", payment.getMethod().name(),
                "paidAmount", order.getPaidAmount(),
                "balanceDue", order.getBalanceDue(),
                "status", order.getStatus().name()));
        return loadDto(order);
    }

    // ---------------------------------------------------------------------
    // Cancel
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "CANCEL", entityType = AGG)
    public CustomerOrderDto cancel(Long orderId, CancelCustomerOrderRequestDto request) {
        CustomerOrder order = requireOrder(orderId);
        if (order.getStatus().isTerminal()) {
            throw new IllegalStateException(
                "Cannot cancel an order in terminal status " + order.getStatus());
        }
        dayGuard.requireOpenDay(order.getBranchId());
        Long actorId = context.userId();

        // Release any reservation (LAYBY only — PRE_ORDER never locked stock).
        if (laybyHasReservation(order)) {
            for (CustomerOrderLine line : linesFor(order.getId())) {
                reservations.release(line.getItemId(), order.getBranchId(), line.getQty(),
                    AGG, order.getId(), "Cancel " + order.getNumber());
            }
        }

        boolean withinWindow = withinRefundWindow(order);
        BigDecimal forfeited = BigDecimal.ZERO;
        if (withinWindow) {
            forfeited = refundAllPaid(order, request.reason(), actorId);
        } else if (order.getPaidAmount().signum() > 0) {
            forfeited = order.getPaidAmount();
        }
        order.cancel(request.reason(), forfeited, actorId);

        events.publish("CustomerOrderCancelled.v1", AGG, String.valueOf(order.getId()),
            Map.of(F_ID, order.getId(), F_NUMBER, order.getNumber(),
                "reason", request.reason(),
                "refundWindowApplied", withinWindow,
                "refundedAmount", order.getRefundedAmount(),
                "forfeitedAmount", order.getForfeitedAmount()));
        return loadDto(order);
    }

    /**
     * For LAYBY orders past the RESERVED transition (DEPOSIT_PAID, PARTIALLY_PAID,
     * READY) we still hold a reservation that needs releasing on cancel.
     */
    private boolean laybyHasReservation(CustomerOrder order) {
        if (order.getType() != CustomerOrderType.LAYBY) return false;
        return switch (order.getStatus()) {
            case RESERVED, DEPOSIT_PAID, PARTIALLY_PAID, READY -> true;
            default -> false;
        };
    }

    // ---------------------------------------------------------------------
    // Mark ready (manual transition for pre-orders pending F7.3)
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "READY", entityType = AGG)
    public CustomerOrderDto markReady(Long orderId) {
        CustomerOrder order = requireOrder(orderId);
        if (order.getType() != CustomerOrderType.PRE_ORDER) {
            throw new IllegalArgumentException(
                "markReady is only valid for PRE_ORDER (was " + order.getType() + ")");
        }
        order.markReady(context.userId());
        events.publish("CustomerOrderReady.v1", AGG, String.valueOf(order.getId()),
            Map.of(F_ID, order.getId(), F_NUMBER, order.getNumber()));
        return loadDto(order);
    }

    // ---------------------------------------------------------------------
    // Collect
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "COLLECT", entityType = AGG)
    public CustomerOrderDto collect(Long orderId) {
        CustomerOrder order = requireOrder(orderId);
        if (order.getStatus() != CustomerOrderStatus.READY) {
            throw new IllegalStateException(
                "Only READY orders can be collected (was " + order.getStatus() + ")");
        }
        if (order.getBalanceDue().signum() != 0) {
            throw new IllegalArgumentException(
                "Order " + order.getNumber() + " has outstanding balance " + order.getBalanceDue());
        }
        dayGuard.requireOpenDay(order.getBranchId());

        Long companyId = order.getCompanyId();
        for (CustomerOrderLine line : linesFor(order.getId())) {
            Item item = requireItem(line.getItemId(), companyId);
            if (item.isBatchTracked()) {
                throw new IllegalArgumentException(
                    "Collection of batch-tracked item " + item.getCode()
                        + " not supported in F7.2; refund and re-sell via POS instead");
            }
            if (order.getType() == CustomerOrderType.LAYBY) {
                reservations.release(line.getItemId(), order.getBranchId(), line.getQty(),
                    AGG, order.getId(), "Collect " + order.getNumber());
            }
            stockMoves.post(new PostStockMoveRequestDto(
                line.getItemId(), order.getBranchId(),
                line.getQty().negate(), null,
                StockMoveType.SALE, AGG, order.getId(),
                "Collect " + order.getNumber(), false, null));
        }
        order.markCollected(context.userId());

        events.publish("CustomerOrderCollected.v1", AGG, String.valueOf(order.getId()),
            Map.of(F_ID, order.getId(), F_NUMBER, order.getNumber(),
                "type", order.getType().name(),
                "branchId", order.getBranchId(),
                "totalAmount", order.getTotalAmount()));
        return loadDto(order);
    }

    // ---------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CustomerOrderDto get(Long orderId) {
        CustomerOrder order = requireOrder(orderId);
        return loadDto(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerOrderDto> list(Long branchId, Long customerId, CustomerOrderStatus status,
                                       CustomerOrderType type) {
        Long companyId = context.companyId();
        List<CustomerOrder> rows;
        if (customerId != null) {
            rows = orders.findByCompanyIdAndCustomerIdOrderByIdDesc(companyId, customerId);
        } else if (branchId != null) {
            rows = orders.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, branchId);
        } else if (status != null) {
            rows = orders.findByCompanyIdAndStatusOrderByIdDesc(companyId, status);
        } else {
            rows = orders.findByCompanyIdOrderByIdDesc(companyId);
        }
        return rows.stream()
            .filter(o -> status == null || o.getStatus() == status)
            .filter(o -> type == null || o.getType() == type)
            .filter(o -> branchId == null || Objects.equals(o.getBranchId(), branchId))
            .map(this::loadDto)
            .toList();
    }

    // ---------------------------------------------------------------------
    // Expiry job
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    public int runExpiryJob() {
        Long systemActor = 0L;
        Instant now = Instant.now();
        List<CustomerOrderStatus> openWithReservation = List.of(
            CustomerOrderStatus.RESERVED,
            CustomerOrderStatus.DEPOSIT_PAID,
            CustomerOrderStatus.PARTIALLY_PAID);
        List<CustomerOrder> due = orders.findByStatusInAndReservedUntilLessThan(
            openWithReservation, now);

        int expired = 0;
        for (CustomerOrder order : due) {
            // System-context release: bypass DayGuard / RequestContext checks
            // would require a privileged path. For MVP the daily job runs in the
            // company's local timezone after day close — if a day is open the
            // release works; if not, we skip the order (and re-attempt next run).
            try {
                if (order.getType() == CustomerOrderType.LAYBY && laybyHasReservation(order)) {
                    for (CustomerOrderLine line : linesFor(order.getId())) {
                        reservations.release(line.getItemId(), order.getBranchId(), line.getQty(),
                            AGG, order.getId(), "Expire " + order.getNumber());
                    }
                }
            } catch (RuntimeException ex) {
                // Defer to next run if the branch's day isn't open.
                continue;
            }
            BigDecimal forfeited = order.getPaidAmount();
            order.expire(forfeited, systemActor);
            events.publish("CustomerOrderExpired.v1", AGG, String.valueOf(order.getId()),
                Map.of(F_ID, order.getId(), F_NUMBER, order.getNumber(),
                    "forfeitedAmount", forfeited,
                    "reservedUntil", order.getReservedUntil() == null
                        ? "" : order.getReservedUntil().toString()));
            expired++;
        }
        return expired;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private CustomerOrder requireOrder(Long id) {
        CustomerOrder order = orders.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Customer order not found: " + id));
        if (!Objects.equals(order.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Customer order not found: " + id);
        }
        return order;
    }

    private void requireCustomer(Long customerId) {
        customers.findById(customerId)
            .orElseThrow(() -> new NoSuchElementException("Customer not found: " + customerId));
    }

    private Item requireItem(Long itemId, Long companyId) {
        Item item = items.findById(itemId)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (!Objects.equals(item.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Item not found: " + itemId);
        }
        return item;
    }

    private String requireCompanyCurrency(Long companyId) {
        return companies.findById(companyId)
            .orElseThrow(() -> new NoSuchElementException("Company not found: " + companyId))
            .getCurrencyCode();
    }

    private void validateLines(List<CreateCustomerOrderRequestDto.Line> input, Long companyId) {
        for (CreateCustomerOrderRequestDto.Line ln : input) {
            requireItem(ln.itemId(), companyId);
            if (ln.qty().signum() <= 0) {
                throw new IllegalArgumentException("Line qty must be positive: " + ln.qty());
            }
        }
    }

    private List<CustomerOrderLine> saveLinesAndRollUp(CustomerOrder order,
                                                       List<CreateCustomerOrderRequestDto.Line> input) {
        List<CustomerOrderLine> saved = new ArrayList<>(input.size());
        BigDecimal total = BigDecimal.ZERO;
        int lineNo = 1;
        for (CreateCustomerOrderRequestDto.Line in : input) {
            Item item = requireItem(in.itemId(), order.getCompanyId());
            Long uomId = in.uomId() != null ? in.uomId() : item.getUomId();
            BigDecimal discount = in.discountAmount() != null ? in.discountAmount() : BigDecimal.ZERO;
            BigDecimal gross = in.qty().multiply(in.unitPrice())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal lineTotal = gross.subtract(discount).max(BigDecimal.ZERO);
            CustomerOrderLine line = lines.save(new CustomerOrderLine(
                order.getId(), lineNo++, in.itemId(), uomId,
                in.qty(), in.unitPrice(), discount, lineTotal, in.notes()));
            saved.add(line);
            total = total.add(lineTotal);
        }
        order.rollUpTotal(total);
        return saved;
    }

    /**
     * Refunds every prior IN payment on its original method. Cash methods write
     * a paired OUT cash_entry; GIFT_CARD calls back into GiftCardService to
     * credit the card. CARD reversals leave no cash trace (the rail handles it).
     * Returns the sum of CARD-only refunds (treated as deferred / off-ledger);
     * tracked as forfeit on cancel-window misses.
     */
    private BigDecimal refundAllPaid(CustomerOrder order, String reason, Long actorId) {
        BigDecimal cardForfeit = BigDecimal.ZERO;
        BusinessDay day = dayGuard.requireOpenDay(order.getBranchId());
        Instant now = Instant.now();
        List<CustomerOrderPayment> priorIn = payments.findByCustomerOrderIdAndDirection(
            order.getId(), OrderPaymentDirection.IN);
        for (CustomerOrderPayment original : priorIn) {
            CustomerOrderPayment refund = payments.save(new CustomerOrderPayment(
                order.getId(), original.getAmount(), original.getMethod(),
                OrderPaymentDirection.OUT, original.getReference(),
                "Refund (cancel): " + reason, now, actorId,
                "refund-" + original.getId()));
            switch (original.getMethod()) {
                case GIFT_CARD -> {
                    GiftCardTxnDto txn = giftCards.refundCredit(original.getReference().trim(),
                        new RefundGiftCardRequestDto(original.getAmount(),
                            AGG + "Refund", refund.getId()));
                    refund.setRefGiftcardTxnId(txn.id());
                }
                case CASH, BANK_TRANSFER, MOBILE_MONEY, CHEQUE -> {
                    CashAccount account = accountForCash(original.getMethod());
                    CashEntryDto entry = cashLedger.post(now, order.getCompanyId(), order.getBranchId(),
                        day.getBusinessDate(), account, CashDirection.OUT,
                        original.getAmount(), BigDecimal.ONE, order.getCurrencyCode(),
                        CashRefType.ORDER_REFUND, refund.getId(),
                        GlCategory.ORDER_DEPOSIT,
                        "Refund " + order.getNumber(), actorId);
                    refund.setRefCashEntryId(entry.id());
                }
                case CARD -> cardForfeit = cardForfeit.add(original.getAmount());
            }
            if (original.getMethod() != OrderPaymentMethod.CARD) {
                order.recordRefund(original.getAmount());
            }
        }
        // CARD refunds need an out-of-band reversal on the card rail — until
        // that lands we report the card portion as forfeited so totals balance.
        return cardForfeit;
    }

    private boolean withinRefundWindow(CustomerOrder order) {
        if (cancelRefundWindowDays <= 0) return false;
        long daysSince = Duration.between(order.getCreatedAt(), Instant.now()).toDays();
        return daysSince <= cancelRefundWindowDays;
    }

    private String resolveNumber(String requested, Long branchId) {
        if (requested != null && !requested.isBlank()) {
            String trimmed = requested.trim().toUpperCase();
            if (orders.existsByBranchIdAndNumber(branchId, trimmed)) {
                throw new IllegalArgumentException(
                    "Order number already exists for this branch: " + trimmed);
            }
            return trimmed;
        }
        // Auto-generate: ORD-BR{branchId}-{epoch-ms} — collision-tolerant against
        // the UNIQUE constraint. Sequence-derived numbering would be nicer but
        // would require an extra round-trip just to peek the seq.
        long suffix = System.currentTimeMillis() % 100_000_000L;
        String candidate = String.format("ORD-BR%d-%08d", branchId, suffix);
        if (orders.existsByBranchIdAndNumber(branchId, candidate)) {
            candidate = candidate + "-" + (suffix % 1000);
        }
        return candidate;
    }

    private Instant defaultReservedUntil(CustomerOrderType type) {
        int days = type == CustomerOrderType.LAYBY
            ? defaultLaybyReserveDays
            : defaultPreOrderReserveDays;
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    private BigDecimal defaultDeposit(BigDecimal total) {
        return total.multiply(depositRequiredPct)
            .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private List<CustomerOrderLine> linesFor(Long orderId) {
        return lines.findByCustomerOrderIdOrderByLineNoAsc(orderId);
    }

    private List<CustomerOrderPayment> paymentsFor(Long orderId) {
        return payments.findByCustomerOrderIdOrderByOccurredAtAsc(orderId);
    }

    private CustomerOrderDto loadDto(CustomerOrder order) {
        return CustomerOrderDto.from(order, linesFor(order.getId()), paymentsFor(order.getId()));
    }

    private static CashAccount accountForCash(OrderPaymentMethod method) {
        return switch (method) {
            case CASH -> CashAccount.CASH_BOX;
            case MOBILE_MONEY -> CashAccount.MOBILE_MONEY;
            case BANK_TRANSFER, CHEQUE -> CashAccount.BANK;
            case CARD, GIFT_CARD -> throw new IllegalArgumentException(
                "No cash account for method " + method);
        };
    }

    private static String eventTypeForCreate(CustomerOrderType type) {
        return type == CustomerOrderType.LAYBY
            ? "LaybyCreated.v1"
            : "PreOrderCreated.v1";
    }

    private String eventTypeForPayment(CustomerOrder order, boolean countsTowardDeposit) {
        if (countsTowardDeposit
                && order.getDepositPaidAmount().compareTo(order.getDepositRequiredAmount()) >= 0) {
            return "OrderDepositPaid.v1";
        }
        return "OrderInstallmentPaid.v1";
    }

}

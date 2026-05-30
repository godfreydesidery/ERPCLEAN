package com.orbix.engine.modules.orders.service;

import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.cash.service.CashLedgerService;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.SettingsService;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.giftcard.service.GiftCardService;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.orders.domain.dto.CancelCustomerOrderRequestDto;
import com.orbix.engine.modules.orders.domain.dto.CustomerOrderDto;
import com.orbix.engine.modules.orders.domain.entity.CustomerOrder;
import com.orbix.engine.modules.orders.domain.entity.CustomerOrderLine;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderStatus;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderType;
import com.orbix.engine.modules.orders.repository.CustomerOrderLineRepository;
import com.orbix.engine.modules.orders.repository.CustomerOrderPaymentRepository;
import com.orbix.engine.modules.orders.repository.CustomerOrderRepository;
import com.orbix.engine.modules.party.repository.CustomerRepository;
import com.orbix.engine.modules.stock.service.StockMoveService;
import com.orbix.engine.modules.stock.service.StockReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CustomerOrderServiceImpl — focused on ISSUE-ORDERS-001 and
 * ISSUE-ORDERS-002 fixes (collect/cancel guard on qtyReserved == 0).
 */
@ExtendWith(MockitoExtension.class)
class CustomerOrderServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID  = 12L;
    private static final Long CUSTOMER_ID = 100L;
    private static final Long ITEM_ID    = 800L;
    private static final Long ACTOR_ID   = 4L;
    private static final Long ORDER_ID   = 1L;
    private static final Long LINE_ID    = 10L;

    @Mock private CustomerOrderRepository orders;
    @Mock private CustomerOrderLineRepository lines;
    @Mock private CustomerOrderPaymentRepository payments;
    @Mock private ItemRepository items;
    @Mock private CustomerRepository customers;
    @Mock private CompanyRepository companies;
    @Mock private CashLedgerService cashLedger;
    @Mock private GiftCardService giftCards;
    @Mock private StockReservationService reservations;
    @Mock private StockMoveService stockMoves;
    @Mock private DayGuard dayGuard;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;
    @Mock private SettingsService settings;

    @InjectMocks private CustomerOrderServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(dayGuard.requireOpenDay(BRANCH_ID))
            .thenReturn(new BusinessDay(BRANCH_ID, LocalDate.of(2026, 5, 30), ACTOR_ID));
        lenient().when(settings.getInt(SettingKey.ORDERS_CANCEL_REFUND_WINDOW_DAYS)).thenReturn(7);
        lenient().when(payments.findByCustomerOrderIdAndDirection(any(), any()))
            .thenReturn(List.of());

        Item item = new Item(COMPANY_ID, "COKE500", "Coke 500ml", ItemType.SELLABLE,
            1L, 1L, 1L, ACTOR_ID);
        item.setId(ITEM_ID);
        lenient().when(items.findById(ITEM_ID)).thenReturn(Optional.of(item));
    }

    // -------------------------------------------------------------------------
    // ISSUE-ORDERS-001: collect succeeds even when qtyReserved == 0
    // -------------------------------------------------------------------------

    @Test
    void collect_laybyReadyWithNoReservation_succeedsWithoutCallingRelease() {
        // Reproduce: a LAYBY order that reached READY through direct payments
        // (skipping the /reserve step) has qtyReserved = 0.  collect() must
        // skip the release call instead of throwing 400.
        CustomerOrder order = readyLayby("ORD-COL-001");
        when(orders.findByUid(order.getUid())).thenReturn(Optional.of(order));
        when(lines.findByCustomerOrderIdOrderByLineNoAsc(ORDER_ID)).thenReturn(List.of(line(1)));
        when(payments.findByCustomerOrderIdOrderByOccurredAtAsc(ORDER_ID)).thenReturn(List.of());
        // qtyReserved == 0 for this item/branch.
        when(reservations.qtyReserved(ITEM_ID, BRANCH_ID)).thenReturn(BigDecimal.ZERO);

        CustomerOrderDto dto = service.collect(order.getUid());

        assertThat(dto.status()).isEqualTo(CustomerOrderStatus.COLLECTED);
        // release() must NOT be called when qtyReserved == 0.
        verify(reservations, never()).release(any(), any(), any(), any(), any(), any());
        // Stock move must still be posted (the SALE move).
        verify(stockMoves).post(any());
        verify(events).publish(org.mockito.ArgumentMatchers.eq("CustomerOrderCollected.v1"),
            any(), any(), any());
    }

    @Test
    void collect_laybyReadyWithReservation_releasesReservation() {
        // When qtyReserved > 0, the release() IS expected.
        CustomerOrder order = readyLayby("ORD-COL-002");
        when(orders.findByUid(order.getUid())).thenReturn(Optional.of(order));
        when(lines.findByCustomerOrderIdOrderByLineNoAsc(ORDER_ID)).thenReturn(List.of(line(1)));
        when(payments.findByCustomerOrderIdOrderByOccurredAtAsc(ORDER_ID)).thenReturn(List.of());
        when(reservations.qtyReserved(ITEM_ID, BRANCH_ID)).thenReturn(new BigDecimal("1"));

        CustomerOrderDto dto = service.collect(order.getUid());

        assertThat(dto.status()).isEqualTo(CustomerOrderStatus.COLLECTED);
        verify(reservations).release(any(), any(), any(), any(), any(), any());
        verify(stockMoves).post(any());
    }

    // -------------------------------------------------------------------------
    // ISSUE-ORDERS-002: cancel succeeds even when qtyReserved == 0
    // -------------------------------------------------------------------------

    @Test
    void cancel_laybyDepositPaidNoReservation_succeedsWithoutCallingRelease() {
        // Reproduce: DEPOSIT_PAID LAYBY, qtyReserved == 0.  cancel() must not
        // attempt release and must always advance to CANCELLED.
        CustomerOrder order = depositPaidLayby("ORD-CXL-001");
        when(orders.findByUid(order.getUid())).thenReturn(Optional.of(order));
        when(lines.findByCustomerOrderIdOrderByLineNoAsc(ORDER_ID)).thenReturn(List.of(line(1)));
        when(payments.findByCustomerOrderIdOrderByOccurredAtAsc(ORDER_ID)).thenReturn(List.of());
        when(reservations.qtyReserved(ITEM_ID, BRANCH_ID)).thenReturn(BigDecimal.ZERO);

        CustomerOrderDto dto = service.cancel(order.getUid(),
            new CancelCustomerOrderRequestDto("Customer no longer wants"));

        assertThat(dto.status()).isEqualTo(CustomerOrderStatus.CANCELLED);
        verify(reservations, never()).release(any(), any(), any(), any(), any(), any());
        verify(events).publish(org.mockito.ArgumentMatchers.eq("CustomerOrderCancelled.v1"),
            any(), any(), any());
    }

    @Test
    void cancel_laybyDepositPaidWithReservation_releasesBeforeCancelling() {
        CustomerOrder order = depositPaidLayby("ORD-CXL-002");
        when(orders.findByUid(order.getUid())).thenReturn(Optional.of(order));
        when(lines.findByCustomerOrderIdOrderByLineNoAsc(ORDER_ID)).thenReturn(List.of(line(1)));
        when(payments.findByCustomerOrderIdOrderByOccurredAtAsc(ORDER_ID)).thenReturn(List.of());
        when(reservations.qtyReserved(ITEM_ID, BRANCH_ID)).thenReturn(new BigDecimal("1"));

        CustomerOrderDto dto = service.cancel(order.getUid(),
            new CancelCustomerOrderRequestDto("Changed mind"));

        assertThat(dto.status()).isEqualTo(CustomerOrderStatus.CANCELLED);
        verify(reservations).release(any(), any(), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** A LAYBY order already in READY status, balance_due == 0. */
    private CustomerOrder readyLayby(String number) {
        CustomerOrder o = new CustomerOrder(number, COMPANY_ID, BRANCH_ID, null,
            CUSTOMER_ID, CustomerOrderType.LAYBY, "TZS",
            Instant.now().plusSeconds(86400 * 30), BigDecimal.ZERO, null, ACTOR_ID);
        o.setId(ORDER_ID);
        ReflectionTestUtils.setField(o, "uid", UidGenerator.next());
        // Simulate fully paid LAYBY reaching READY.
        ReflectionTestUtils.setField(o, "status", CustomerOrderStatus.READY);
        ReflectionTestUtils.setField(o, "totalAmount", new BigDecimal("5000"));
        ReflectionTestUtils.setField(o, "paidAmount", new BigDecimal("5000"));
        ReflectionTestUtils.setField(o, "balanceDue", BigDecimal.ZERO);
        return o;
    }

    /** A LAYBY order in DEPOSIT_PAID status (partial payment, qtyReserved=0). */
    private CustomerOrder depositPaidLayby(String number) {
        CustomerOrder o = new CustomerOrder(number, COMPANY_ID, BRANCH_ID, null,
            CUSTOMER_ID, CustomerOrderType.LAYBY, "TZS",
            Instant.now().plusSeconds(86400 * 30), new BigDecimal("2000"), null, ACTOR_ID);
        o.setId(ORDER_ID);
        ReflectionTestUtils.setField(o, "uid", UidGenerator.next());
        ReflectionTestUtils.setField(o, "status", CustomerOrderStatus.DEPOSIT_PAID);
        ReflectionTestUtils.setField(o, "totalAmount", new BigDecimal("5000"));
        ReflectionTestUtils.setField(o, "paidAmount", new BigDecimal("2000"));
        ReflectionTestUtils.setField(o, "depositPaidAmount", new BigDecimal("2000"));
        ReflectionTestUtils.setField(o, "balanceDue", new BigDecimal("3000"));
        return o;
    }

    private CustomerOrderLine line(int lineNo) {
        CustomerOrderLine l = new CustomerOrderLine(ORDER_ID, lineNo,
            ITEM_ID, 1L, BigDecimal.ONE, new BigDecimal("5000"),
            BigDecimal.ZERO, new BigDecimal("5000"), null);
        l.setId(LINE_ID);
        return l;
    }
}

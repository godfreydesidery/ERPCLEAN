package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.SettingsService;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.procurement.domain.dto.CreateLpoOrderRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.LpoOrderDto;
import com.orbix.engine.modules.procurement.domain.entity.LpoOrder;
import com.orbix.engine.modules.procurement.domain.entity.LpoOrderLine;
import com.orbix.engine.modules.procurement.domain.enums.LpoOrderStatus;
import com.orbix.engine.modules.procurement.repository.LpoOrderLineRepository;
import com.orbix.engine.modules.procurement.repository.LpoOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LpoOrderServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long SUPPLIER_ID = 808L;
    private static final Long ITEM_ID = 8801L;
    private static final Long UOM_ID = 1L;
    private static final Long VAT_GROUP_ID = 2L;
    private static final Long ACTOR_ID = 4L;

    @Mock private LpoOrderRepository orders;
    @Mock private LpoOrderLineRepository lines;
    @Mock private ItemRepository items;
    @Mock private VatGroupRepository vatGroups;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;
    @Mock private SettingsService settings;

    @InjectMocks private LpoOrderServiceImpl service;

    private final AtomicLong nextId = new AtomicLong(1000);

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(settings.getDecimal(SettingKey.PROCUREMENT_LPO_AUTO_APPROVAL))
            .thenReturn(BigDecimal.ZERO);
        Item item = new Item(COMPANY_ID, "SKU1", "Sugar", ItemType.BOTH, 10L, UOM_ID, VAT_GROUP_ID, ACTOR_ID);
        item.setId(ITEM_ID);
        lenient().when(items.findById(ITEM_ID)).thenReturn(Optional.of(item));
        VatGroup vat = new VatGroup(COMPANY_ID, "STD", "Standard", new BigDecimal("0.18"),
            LocalDate.of(2020, 1, 1), true, ACTOR_ID);
        vat.setId(VAT_GROUP_ID);
        lenient().when(vatGroups.findById(VAT_GROUP_ID)).thenReturn(Optional.of(vat));
        lenient().when(orders.save(any(LpoOrder.class))).thenAnswer(inv -> {
            LpoOrder o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(nextId.getAndIncrement());
            }
            return o;
        });
        lenient().when(lines.save(any(LpoOrderLine.class))).thenAnswer(inv -> {
            LpoOrderLine l = inv.getArgument(0);
            l.setId(nextId.getAndIncrement());
            return l;
        });
        lenient().when(lines.findByLpoOrderIdOrderByLineNoAsc(any()))
            .thenAnswer(inv -> List.<LpoOrderLine>of());
    }

    private static CreateLpoOrderRequestDto draft(String number, BigDecimal qty, BigDecimal price,
                                                  BigDecimal discountPct) {
        return new CreateLpoOrderRequestDto(
            number, BRANCH_ID, SUPPLIER_ID,
            LocalDate.of(2026, 5, 15), LocalDate.of(2026, 5, 20),
            "TZS", "test order",
            List.of(new CreateLpoOrderRequestDto.Line(
                ITEM_ID, UOM_ID, qty, price, VAT_GROUP_ID, discountPct
            ))
        );
    }

    @Test
    void createDraft_rollsUpSubtotalAndTax_andEmitsCreated() {
        when(orders.existsByBranchIdAndNumber(BRANCH_ID, "LPO-1")).thenReturn(false);

        LpoOrderDto dto = service.createDraft(draft("LPO-1", new BigDecimal("10"),
            new BigDecimal("100"), BigDecimal.ZERO));

        assertThat(dto.status()).isEqualTo(LpoOrderStatus.DRAFT);
        // 10 * 100 = 1000 subtotal; 18% tax = 180; total 1180
        assertThat(dto.subtotalAmount()).isEqualByComparingTo("1000");
        assertThat(dto.taxAmount()).isEqualByComparingTo("180");
        assertThat(dto.totalAmount()).isEqualByComparingTo("1180");
        assertThat(dto.lines()).hasSize(1);
        assertThat(dto.lines().get(0).lineTotal()).isEqualByComparingTo("1000");
        verify(events).publish(eq("LpoOrderCreated.v1"), any(), any(), any());
    }

    @Test
    void createDraft_appliesLineDiscount() {
        when(orders.existsByBranchIdAndNumber(BRANCH_ID, "LPO-2")).thenReturn(false);

        // 10 * 100 * (1 - 0.10) = 900 net; tax = 162
        LpoOrderDto dto = service.createDraft(draft("LPO-2", new BigDecimal("10"),
            new BigDecimal("100"), new BigDecimal("10")));

        assertThat(dto.subtotalAmount()).isEqualByComparingTo("900");
        assertThat(dto.taxAmount()).isEqualByComparingTo("162");
        assertThat(dto.totalAmount()).isEqualByComparingTo("1062");
    }

    @Test
    void createDraft_rejectsDuplicateNumberOnSameBranch() {
        when(orders.existsByBranchIdAndNumber(BRANCH_ID, "LPO-3")).thenReturn(true);

        CreateLpoOrderRequestDto request = draft("LPO-3", BigDecimal.ONE, BigDecimal.ONE, null);
        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(orders, never()).save(any());
    }

    @Test
    void submit_belowOrAtThreshold_autoApproves_andEmitsApproved() {
        when(settings.getDecimal(SettingKey.PROCUREMENT_LPO_AUTO_APPROVAL))
            .thenReturn(new BigDecimal("5000"));
        LpoOrder order = createdOrder("LPO-AUTO", new BigDecimal("1000"));
        when(orders.findByUid(order.getUid())).thenReturn(Optional.of(order));

        LpoOrderDto dto = service.submit(order.getUid());

        assertThat(dto.status()).isEqualTo(LpoOrderStatus.APPROVED);
        assertThat(dto.approvedBy()).isEqualTo(ACTOR_ID);
        verify(events).publish(eq("LpoOrderApproved.v1"), any(), any(), any());
        verify(events, never()).publish(eq("LpoOrderSubmitted.v1"), any(), any(), any());
    }

    @Test
    void submit_aboveThreshold_goesToPendingApproval() {
        when(settings.getDecimal(SettingKey.PROCUREMENT_LPO_AUTO_APPROVAL))
            .thenReturn(new BigDecimal("5000"));
        LpoOrder order = createdOrder("LPO-BIG", new BigDecimal("10000"));
        when(orders.findByUid(order.getUid())).thenReturn(Optional.of(order));

        LpoOrderDto dto = service.submit(order.getUid());

        assertThat(dto.status()).isEqualTo(LpoOrderStatus.PENDING_APPROVAL);
        verify(events).publish(eq("LpoOrderSubmitted.v1"), any(), any(), any());
        verify(events, never()).publish(eq("LpoOrderApproved.v1"), any(), any(), any());
    }

    @Test
    void submit_thresholdZero_alwaysGoesToPendingApproval() {
        LpoOrder order = createdOrder("LPO-NOAUTO", new BigDecimal("10"));
        when(orders.findByUid(order.getUid())).thenReturn(Optional.of(order));

        LpoOrderDto dto = service.submit(order.getUid());

        assertThat(dto.status()).isEqualTo(LpoOrderStatus.PENDING_APPROVAL);
    }

    @Test
    void approve_pendingApproval_setsApprovedFields() {
        LpoOrder order = createdOrder("LPO-APR", new BigDecimal("10000"));
        order.submit(ACTOR_ID);
        when(orders.findByUid(order.getUid())).thenReturn(Optional.of(order));

        LpoOrderDto dto = service.approve(order.getUid());

        assertThat(dto.status()).isEqualTo(LpoOrderStatus.APPROVED);
        assertThat(dto.approvedBy()).isEqualTo(ACTOR_ID);
        verify(events).publish(eq("LpoOrderApproved.v1"), any(), any(), any());
    }

    @Test
    void approve_draftDirectly_isRejected() {
        LpoOrder order = createdOrder("LPO-DRAFT", new BigDecimal("10000"));
        when(orders.findByUid(order.getUid())).thenReturn(Optional.of(order));

        String uid = order.getUid();
        assertThatThrownBy(() -> service.approve(uid))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PENDING_APPROVAL");
    }

    @Test
    void cancel_fromDraft_succeeds() {
        LpoOrder order = createdOrder("LPO-CXL", new BigDecimal("10"));
        when(orders.findByUid(order.getUid())).thenReturn(Optional.of(order));

        LpoOrderDto dto = service.cancel(order.getUid());

        assertThat(dto.status()).isEqualTo(LpoOrderStatus.CANCELLED);
        verify(events).publish(eq("LpoOrderCancelled.v1"), any(), any(), any());
    }

    @Test
    void cancel_fromApproved_isRejected() {
        LpoOrder order = createdOrder("LPO-CXLA", new BigDecimal("10"));
        order.approve(ACTOR_ID);
        when(orders.findByUid(order.getUid())).thenReturn(Optional.of(order));

        String uid = order.getUid();
        assertThatThrownBy(() -> service.cancel(uid))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void get_foreignCompany_throwsNotFound() {
        LpoOrder foreign = new LpoOrder("LPO-X", 999L, BRANCH_ID, SUPPLIER_ID,
            LocalDate.now(), null, "TZS", null, ACTOR_ID);
        foreign.setId(900L);
        ReflectionTestUtils.setField(foreign, "uid", UidGenerator.next());
        when(orders.findByUid(foreign.getUid())).thenReturn(Optional.of(foreign));

        String uid = foreign.getUid();
        assertThatThrownBy(() -> service.get(uid)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updateDraft_replacesLinesAndReRollsTotals() {
        LpoOrder order = createdOrder("LPO-UPD", new BigDecimal("1000"));
        when(orders.findByUid(order.getUid())).thenReturn(Optional.of(order));

        var request = new com.orbix.engine.modules.procurement.domain.dto.UpdateLpoOrderRequestDto(
            SUPPLIER_ID, LocalDate.of(2026, 5, 16), null, "TZS", "edited",
            List.of(new CreateLpoOrderRequestDto.Line(
                ITEM_ID, UOM_ID, new BigDecimal("20"), new BigDecimal("50"), VAT_GROUP_ID, BigDecimal.ZERO
            ))
        );

        LpoOrderDto dto = service.updateDraft(order.getUid(), request);

        // 20 * 50 = 1000 subtotal; 180 tax; 1180 total
        assertThat(dto.subtotalAmount()).isEqualByComparingTo("1000");
        assertThat(dto.totalAmount()).isEqualByComparingTo("1180");
        verify(lines).deleteByLpoOrderId(order.getId());
    }

    private LpoOrder createdOrder(String number, BigDecimal total) {
        LpoOrder order = new LpoOrder(number, COMPANY_ID, BRANCH_ID, SUPPLIER_ID,
            LocalDate.of(2026, 5, 15), null, "TZS", null, ACTOR_ID);
        order.setId(nextId.getAndIncrement());
        ReflectionTestUtils.setField(order, "uid", UidGenerator.next());
        order.rollUpTotals(total, BigDecimal.ZERO);
        return order;
    }
}

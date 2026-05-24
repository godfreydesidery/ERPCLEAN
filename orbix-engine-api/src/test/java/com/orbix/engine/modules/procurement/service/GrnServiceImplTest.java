package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.procurement.domain.dto.CreateGrnRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.GrnDto;
import com.orbix.engine.modules.procurement.domain.entity.Grn;
import com.orbix.engine.modules.procurement.domain.entity.GrnLine;
import com.orbix.engine.modules.procurement.domain.entity.LpoOrder;
import com.orbix.engine.modules.procurement.domain.entity.LpoOrderLine;
import com.orbix.engine.modules.procurement.domain.enums.GrnStatus;
import com.orbix.engine.modules.procurement.domain.enums.LpoOrderStatus;
import com.orbix.engine.modules.procurement.repository.GrnLineRepository;
import com.orbix.engine.modules.procurement.repository.GrnRepository;
import com.orbix.engine.modules.procurement.repository.LpoOrderLineRepository;
import com.orbix.engine.modules.procurement.repository.LpoOrderRepository;
import com.orbix.engine.modules.stock.domain.dto.CreateStockBatchRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockBatchDto;
import com.orbix.engine.modules.stock.domain.enums.StockBatchStatus;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.stock.service.StockBatchService;
import com.orbix.engine.modules.stock.service.StockMoveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
class GrnServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long SUPPLIER_ID = 808L;
    private static final Long ITEM_ID = 8801L;
    private static final Long UOM_ID = 1L;
    private static final Long VAT_GROUP_ID = 2L;
    private static final Long ACTOR_ID = 4L;

    @Mock private GrnRepository grns;
    @Mock private GrnLineRepository grnLines;
    @Mock private LpoOrderRepository lpos;
    @Mock private LpoOrderLineRepository lpoLines;
    @Mock private ItemRepository items;
    @Mock private VatGroupRepository vatGroups;
    @Mock private StockMoveService stockMoveService;
    @Mock private StockBatchService stockBatchService;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;

    @InjectMocks private GrnServiceImpl service;

    private final AtomicLong nextId = new AtomicLong(1000);

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);

        Item item = new Item(COMPANY_ID, "SKU1", "Sugar", ItemType.BOTH, 10L, UOM_ID, VAT_GROUP_ID, ACTOR_ID);
        item.setId(ITEM_ID);
        lenient().when(items.findById(ITEM_ID)).thenReturn(Optional.of(item));

        VatGroup vat = new VatGroup(COMPANY_ID, "STD", "Standard", new BigDecimal("0.18"),
            LocalDate.of(2020, 1, 1), true, ACTOR_ID);
        vat.setId(VAT_GROUP_ID);
        lenient().when(vatGroups.findById(VAT_GROUP_ID)).thenReturn(Optional.of(vat));

        lenient().when(grns.save(any(Grn.class))).thenAnswer(inv -> {
            Grn g = inv.getArgument(0);
            if (g.getId() == null) g.setId(nextId.getAndIncrement());
            return g;
        });
        lenient().when(grnLines.save(any(GrnLine.class))).thenAnswer(inv -> {
            GrnLine l = inv.getArgument(0);
            l.setId(nextId.getAndIncrement());
            return l;
        });
        lenient().when(grnLines.findByGrnIdOrderByIdAsc(any()))
            .thenAnswer(inv -> List.<GrnLine>of());
    }

    private CreateGrnRequestDto.Line line(Long lpoLineId, BigDecimal qty, BigDecimal cost,
                                          String batchNo, LocalDate expiry) {
        return new CreateGrnRequestDto.Line(
            lpoLineId, ITEM_ID, UOM_ID, qty, cost, VAT_GROUP_ID, batchNo, expiry
        );
    }

    private CreateGrnRequestDto draft(String number, Long lpoOrderId,
                                      List<CreateGrnRequestDto.Line> lines) {
        return new CreateGrnRequestDto(
            number, BRANCH_ID, SUPPLIER_ID, lpoOrderId,
            LocalDate.of(2026, 5, 16), "DN-001", "test grn",
            lines
        );
    }

    private LpoOrder approvedLpo() {
        LpoOrder lpo = new LpoOrder("LPO-A", COMPANY_ID, BRANCH_ID, SUPPLIER_ID,
            LocalDate.of(2026, 5, 15), null, "TZS", null, ACTOR_ID);
        lpo.setId(500L);
        lpo.approve(ACTOR_ID);
        return lpo;
    }

    private LpoOrderLine lpoLine(Long id, BigDecimal orderedQty, BigDecimal alreadyReceived) {
        LpoOrderLine l = new LpoOrderLine(500L, 1, ITEM_ID, UOM_ID, orderedQty,
            new BigDecimal("100"), VAT_GROUP_ID, BigDecimal.ZERO, BigDecimal.ZERO);
        l.setId(id);
        if (alreadyReceived.signum() > 0) {
            l.addReceived(alreadyReceived);
        }
        return l;
    }

    @Test
    void createDraft_lpoBound_rollsUpTotalsAndEmitsCreated() {
        LpoOrder lpo = approvedLpo();
        when(lpos.findById(500L)).thenReturn(Optional.of(lpo));
        LpoOrderLine line = lpoLine(601L, new BigDecimal("100"), BigDecimal.ZERO);
        when(lpoLines.findByLpoOrderIdOrderByLineNoAsc(500L)).thenReturn(List.of(line));

        GrnDto dto = service.createDraft(draft("GRN-1", 500L, List.of(
            line(601L, new BigDecimal("10"), new BigDecimal("90"), null, null))));

        // 10 * 90 = 900 subtotal; tax 18% = 162; total 1062
        assertThat(dto.subtotalAmount()).isEqualByComparingTo("900");
        assertThat(dto.taxAmount()).isEqualByComparingTo("162");
        assertThat(dto.totalAmount()).isEqualByComparingTo("1062");
        assertThat(dto.status()).isEqualTo(GrnStatus.DRAFT);
        verify(events).publish(eq("GrnCreated.v1"), any(), any(), any());
    }

    @Test
    void createDraft_overReceipt_isRejected() {
        LpoOrder lpo = approvedLpo();
        when(lpos.findById(500L)).thenReturn(Optional.of(lpo));
        LpoOrderLine line = lpoLine(601L, new BigDecimal("10"), new BigDecimal("8"));
        when(lpoLines.findByLpoOrderIdOrderByLineNoAsc(500L)).thenReturn(List.of(line));

        CreateGrnRequestDto request = draft("GRN-OVER", 500L, List.of(
            line(601L, new BigDecimal("5"), new BigDecimal("90"), null, null)));

        // Outstanding is 10-8 = 2; requesting 5 must fail
        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Over-receipt");
        verify(grns, never()).save(any());
    }

    @Test
    void createDraft_lpoNotApproved_isRejected() {
        LpoOrder draft = new LpoOrder("LPO-D", COMPANY_ID, BRANCH_ID, SUPPLIER_ID,
            LocalDate.now(), null, "TZS", null, ACTOR_ID);
        draft.setId(501L);
        when(lpos.findById(501L)).thenReturn(Optional.of(draft));

        CreateGrnRequestDto request = draft("GRN-X", 501L, List.of(
            line(null, new BigDecimal("1"), new BigDecimal("100"), null, null)));

        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("APPROVED");
    }

    @Test
    void createDraft_batchTrackedItemRequiresBatchNo() {
        Item batched = new Item(COMPANY_ID, "MILK", "Milk", ItemType.BOTH, 10L, UOM_ID, VAT_GROUP_ID, ACTOR_ID);
        batched.setId(ITEM_ID);
        batched.setBatchTracked(true);
        when(items.findById(ITEM_ID)).thenReturn(Optional.of(batched));

        CreateGrnRequestDto request = draft("GRN-B", null, List.of(
            line(null, new BigDecimal("5"), new BigDecimal("100"), null, null)));

        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("batchNo is required");
    }

    @Test
    void post_writesStockMoveForEachLine_andEmitsGrnPosted() {
        Grn grn = postable(null);
        GrnLine line = grnLineRow(grn.getId(), null, new BigDecimal("10"), new BigDecimal("90"), null, null);
        when(grns.findByUid(grn.getUid())).thenReturn(Optional.of(grn));
        when(grnLines.findByGrnIdOrderByIdAsc(grn.getId())).thenReturn(List.of(line));

        service.post(grn.getUid());

        ArgumentCaptor<PostStockMoveRequestDto> captor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(captor.capture());
        PostStockMoveRequestDto posted = captor.getValue();
        assertThat(posted.moveType()).isEqualTo(StockMoveType.GRN);
        assertThat(posted.qty()).isEqualByComparingTo("10");
        assertThat(posted.unitCost()).isEqualByComparingTo("90");
        assertThat(posted.batchId()).isNull();
        assertThat(grn.getStatus()).isEqualTo(GrnStatus.POSTED);
        verify(events).publish(eq("GrnPosted.v1"), any(), any(), any());
    }

    @Test
    void post_batchTrackedItem_createsBatchAndStampsBatchIdOnMove() {
        Item batched = new Item(COMPANY_ID, "MILK", "Milk", ItemType.BOTH, 10L, UOM_ID, VAT_GROUP_ID, ACTOR_ID);
        batched.setId(ITEM_ID);
        batched.setBatchTracked(true);
        when(items.findById(ITEM_ID)).thenReturn(Optional.of(batched));

        Grn grn = postable(null);
        GrnLine line = grnLineRow(grn.getId(), null, new BigDecimal("10"), new BigDecimal("90"),
            "B-001", LocalDate.of(2026, 12, 31));
        when(grns.findByUid(grn.getUid())).thenReturn(Optional.of(grn));
        when(grnLines.findByGrnIdOrderByIdAsc(grn.getId())).thenReturn(List.of(line));
        when(stockBatchService.createBatch(any(CreateStockBatchRequestDto.class)))
            .thenReturn(new StockBatchDto(7777L, "01HZ8X7M3K9PJK2D7Q5BCN8W4F", ITEM_ID, BRANCH_ID, COMPANY_ID, "B-001",
                null, LocalDate.of(2026, 12, 31),
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("90"),
                "Grn", grn.getId(), StockBatchStatus.ACTIVE));

        service.post(grn.getUid());

        ArgumentCaptor<PostStockMoveRequestDto> captor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(captor.capture());
        assertThat(captor.getValue().batchId()).isEqualTo(7777L);
        verify(stockBatchService).createBatch(any(CreateStockBatchRequestDto.class));
    }

    @Test
    void post_lpoBound_partialFlipsLpoToPartiallyReceived() {
        LpoOrder lpo = approvedLpo();
        when(lpos.findById(500L)).thenReturn(Optional.of(lpo));
        LpoOrderLine lpoLine = lpoLine(601L, new BigDecimal("10"), BigDecimal.ZERO);
        when(lpoLines.findByLpoOrderIdOrderByLineNoAsc(500L)).thenReturn(List.of(lpoLine));

        Grn grn = postable(500L);
        GrnLine line = grnLineRow(grn.getId(), 601L, new BigDecimal("6"), new BigDecimal("90"), null, null);
        when(grns.findByUid(grn.getUid())).thenReturn(Optional.of(grn));
        when(grnLines.findByGrnIdOrderByIdAsc(grn.getId())).thenReturn(List.of(line));

        service.post(grn.getUid());

        assertThat(lpo.getStatus()).isEqualTo(LpoOrderStatus.PARTIALLY_RECEIVED);
        assertThat(lpoLine.getReceivedQty()).isEqualByComparingTo("6");
    }

    @Test
    void post_lpoBound_fullReceiptFlipsLpoToReceived() {
        LpoOrder lpo = approvedLpo();
        when(lpos.findById(500L)).thenReturn(Optional.of(lpo));
        LpoOrderLine lpoLine = lpoLine(601L, new BigDecimal("10"), BigDecimal.ZERO);
        when(lpoLines.findByLpoOrderIdOrderByLineNoAsc(500L)).thenReturn(List.of(lpoLine));

        Grn grn = postable(500L);
        GrnLine line = grnLineRow(grn.getId(), 601L, new BigDecimal("10"), new BigDecimal("90"), null, null);
        when(grns.findByUid(grn.getUid())).thenReturn(Optional.of(grn));
        when(grnLines.findByGrnIdOrderByIdAsc(grn.getId())).thenReturn(List.of(line));

        service.post(grn.getUid());

        assertThat(lpo.getStatus()).isEqualTo(LpoOrderStatus.RECEIVED);
        assertThat(lpoLine.isFullyReceived()).isTrue();
    }

    @Test
    void cancel_fromDraft_succeeds() {
        Grn grn = postable(null);
        when(grns.findByUid(grn.getUid())).thenReturn(Optional.of(grn));

        GrnDto dto = service.cancel(grn.getUid());

        assertThat(dto.status()).isEqualTo(GrnStatus.CANCELLED);
        verify(events).publish(eq("GrnCancelled.v1"), any(), any(), any());
    }

    @Test
    void createDraft_rejectsDuplicateNumber() {
        when(grns.existsByBranchIdAndNumber(BRANCH_ID, "GRN-DUP")).thenReturn(true);

        CreateGrnRequestDto request = draft("GRN-DUP", null,
            List.of(line(null, new BigDecimal("1"), new BigDecimal("1"), null, null)));
        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    private Grn postable(Long lpoOrderId) {
        Grn grn = new Grn("GRN-P", COMPANY_ID, BRANCH_ID, SUPPLIER_ID, lpoOrderId,
            LocalDate.of(2026, 5, 16), null, null, ACTOR_ID);
        grn.setId(nextId.getAndIncrement());
        ReflectionTestUtils.setField(grn, "uid", UidGenerator.next());
        return grn;
    }

    private GrnLine grnLineRow(Long grnId, Long lpoLineId, BigDecimal qty, BigDecimal cost,
                               String batchNo, LocalDate expiry) {
        BigDecimal total = qty.multiply(cost);
        GrnLine line = new GrnLine(grnId, lpoLineId, ITEM_ID, UOM_ID, qty, cost, VAT_GROUP_ID,
            total, batchNo, expiry);
        line.setId(nextId.getAndIncrement());
        return line;
    }
}

package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.stock.domain.dto.BatchPickDto;
import com.orbix.engine.modules.stock.domain.dto.PostInternalConsumptionRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.entity.StockBatch;
import com.orbix.engine.modules.stock.domain.enums.ConsumptionCategory;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.repository.StockBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalConsumptionServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long SECTION_ID = 33L;
    private static final Long ITEM_ID = 8801L;
    private static final Long ACTOR_ID = 4L;
    private static final Long AUTHORISER_ID = 9L;

    @Mock private StockMoveService stockMoveService;
    @Mock private StockBatchService stockBatchService;
    @Mock private StockBatchRepository stockBatchRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private PermissionResolverService permissions;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;

    @InjectMocks private InternalConsumptionServiceImpl service;

    /** Non-batch-tracked item stub returned by default for most tests. */
    private Item plainItem;
    /** Batch-tracked item stub. */
    private Item batchItem;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);

        plainItem = new Item(COMPANY_ID, "SKU-PLAIN", "Plain Item", ItemType.SELLABLE, 1L, 1L, 1L, ACTOR_ID);
        plainItem.setId(ITEM_ID);
        ReflectionTestUtils.setField(plainItem, "uid", UidGenerator.next());

        batchItem = new Item(COMPANY_ID, "SKU-BATCH", "Batch Item", ItemType.SELLABLE, 1L, 1L, 1L, ACTOR_ID);
        batchItem.setId(ITEM_ID);
        batchItem.applyBatchTracking(true, ACTOR_ID);
        ReflectionTestUtils.setField(batchItem, "uid", UidGenerator.next());

        // authoriser has permission by default for happy-path tests
        lenient().when(permissions.resolve(AUTHORISER_ID, COMPANY_ID, null))
            .thenReturn(Set.of(InternalConsumptionServiceImpl.AUTHORISER_PERMISSION));
        // item lookup returns plainItem by default
        lenient().when(itemRepository.findById(ITEM_ID))
            .thenReturn(Optional.of(plainItem));
        // stockMoveService returns null — return value is not asserted in unit tests
        lenient().when(stockMoveService.post(any())).thenReturn(null);
    }

    private static PostInternalConsumptionRequestDto req(BigDecimal qty,
                                                         ConsumptionCategory category,
                                                         Long authoriserId,
                                                         Long batchId) {
        return new PostInternalConsumptionRequestDto(ITEM_ID, BRANCH_ID, qty, category, SECTION_ID,
            authoriserId, "staff lunch", batchId);
    }

    // -----------------------------------------------------------------------
    // Original guard tests (unchanged behaviour)
    // -----------------------------------------------------------------------

    @Test
    void post_nonBatchItem_emitsOutboundMoveWithCategoryAndAuthoriser() {
        service.postInternalConsumption(req(new BigDecimal("5"), ConsumptionCategory.CANTEEN, AUTHORISER_ID, null));

        ArgumentCaptor<PostStockMoveRequestDto> captor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(captor.capture());
        PostStockMoveRequestDto posted = captor.getValue();
        assertThat(posted.qty()).isEqualByComparingTo("-5");
        assertThat(posted.moveType()).isEqualTo(StockMoveType.INTERNAL_CONSUMPTION);
        assertThat(posted.consumptionCategory()).isEqualTo(ConsumptionCategory.CANTEEN);
        assertThat(posted.sectionId()).isEqualTo(SECTION_ID);
        assertThat(posted.authorisedByUserId()).isEqualTo(AUTHORISER_ID);
        assertThat(posted.batchId()).isNull();
        // drainFefo must NOT be called for a non-batch-tracked item
        verify(stockBatchService, never()).drainFefo(any(), any(), any());
    }

    @Test
    void post_authoriserIsCaller_isRejected() {
        PostInternalConsumptionRequestDto request =
            req(new BigDecimal("5"), ConsumptionCategory.CANTEEN, ACTOR_ID, null);
        assertThatThrownBy(() -> service.postInternalConsumption(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("your own");
        verify(stockMoveService, never()).post(any());
    }

    @Test
    void post_authoriserWithoutPermission_403() {
        when(permissions.resolve(AUTHORISER_ID, COMPANY_ID, null)).thenReturn(Set.of());

        PostInternalConsumptionRequestDto request =
            req(new BigDecimal("5"), ConsumptionCategory.DISPLAY, AUTHORISER_ID, null);
        assertThatThrownBy(() -> service.postInternalConsumption(request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining(InternalConsumptionServiceImpl.AUTHORISER_PERMISSION);
        verify(stockMoveService, never()).post(any());
    }

    // -----------------------------------------------------------------------
    // ISSUE-CB-001: FEFO drain for batch-tracked item (batchId=null)
    // -----------------------------------------------------------------------

    @Test
    void post_batchTrackedItem_nullBatchId_drainsFefoAndStampsPickBatchId() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(batchItem));

        BatchPickDto pick = new BatchPickDto(42L, "B-001", new BigDecimal("5"), new BigDecimal("120"));
        when(stockBatchService.drainFefo(ITEM_ID, BRANCH_ID, new BigDecimal("5")))
            .thenReturn(List.of(pick));

        service.postInternalConsumption(req(new BigDecimal("5"), ConsumptionCategory.CANTEEN, AUTHORISER_ID, null));

        verify(stockBatchService).drainFefo(ITEM_ID, BRANCH_ID, new BigDecimal("5"));

        ArgumentCaptor<PostStockMoveRequestDto> captor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(captor.capture());
        PostStockMoveRequestDto posted = captor.getValue();
        assertThat(posted.qty()).isEqualByComparingTo("-5");
        assertThat(posted.batchId()).isEqualTo(42L);
        assertThat(posted.moveType()).isEqualTo(StockMoveType.INTERNAL_CONSUMPTION);
    }

    @Test
    void post_batchTrackedItem_fefoMultiplePicks_emitsOneMovePerPick() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(batchItem));

        List<BatchPickDto> picks = List.of(
            new BatchPickDto(1L, "B-OLD", new BigDecimal("4"), new BigDecimal("100")),
            new BatchPickDto(2L, "B-MID", new BigDecimal("3"), new BigDecimal("110"))
        );
        when(stockBatchService.drainFefo(ITEM_ID, BRANCH_ID, new BigDecimal("7")))
            .thenReturn(picks);

        service.postInternalConsumption(req(new BigDecimal("7"), ConsumptionCategory.CANTEEN, AUTHORISER_ID, null));

        // One stock move per batch pick
        ArgumentCaptor<PostStockMoveRequestDto> captor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService, times(2)).post(captor.capture());
        List<PostStockMoveRequestDto> posted = captor.getAllValues();
        assertThat(posted.get(0).batchId()).isEqualTo(1L);
        assertThat(posted.get(0).qty()).isEqualByComparingTo("-4");
        assertThat(posted.get(1).batchId()).isEqualTo(2L);
        assertThat(posted.get(1).qty()).isEqualByComparingTo("-3");
    }

    @Test
    void post_batchTrackedItem_insufficientBatches_throwsBeforeMove() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(batchItem));
        when(stockBatchService.drainFefo(ITEM_ID, BRANCH_ID, new BigDecimal("99")))
            .thenThrow(new IllegalArgumentException("Insufficient active batches"));

        assertThatThrownBy(() -> service.postInternalConsumption(
                req(new BigDecimal("99"), ConsumptionCategory.CANTEEN, AUTHORISER_ID, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Insufficient active batches");
        // No stock move must have been written
        verify(stockMoveService, never()).post(any());
    }

    // -----------------------------------------------------------------------
    // ISSUE-CB-002: Explicit batchId drains that batch directly
    // -----------------------------------------------------------------------

    @Test
    void post_batchTrackedItem_explicitBatchId_drainsBatchAndStampsId() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(batchItem));

        StockBatch batch = new StockBatch(ITEM_ID, BRANCH_ID, COMPANY_ID, "B-EXP",
            null, LocalDate.of(2026, 6, 1), new BigDecimal("5"), new BigDecimal("130"), "GRN", 1L, ACTOR_ID);
        batch.setId(77L);
        ReflectionTestUtils.setField(batch, "uid", UidGenerator.next());
        when(stockBatchRepository.findById(77L)).thenReturn(Optional.of(batch));

        service.postInternalConsumption(req(new BigDecimal("2"), ConsumptionCategory.CANTEEN, AUTHORISER_ID, 77L));

        // drainFefo must NOT be called — explicit batch path bypasses FEFO picker
        verify(stockBatchService, never()).drainFefo(any(), any(), any());

        // batch.drain() is called in-place; verify qty_on_hand decreased
        assertThat(batch.getQtyOnHand()).isEqualByComparingTo("3");

        ArgumentCaptor<PostStockMoveRequestDto> captor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(captor.capture());
        PostStockMoveRequestDto posted = captor.getValue();
        assertThat(posted.qty()).isEqualByComparingTo("-2");
        assertThat(posted.batchId()).isEqualTo(77L);
        assertThat(posted.moveType()).isEqualTo(StockMoveType.INTERNAL_CONSUMPTION);
    }

    @Test
    void post_batchTrackedItem_explicitBatchId_fullConsume_batchExhausted_noSubsequentWriteOff() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(batchItem));

        StockBatch batch = new StockBatch(ITEM_ID, BRANCH_ID, COMPANY_ID, "B-FULL",
            null, LocalDate.of(2026, 6, 1), new BigDecimal("2"), new BigDecimal("100"), "GRN", 1L, ACTOR_ID);
        batch.setId(88L);
        ReflectionTestUtils.setField(batch, "uid", UidGenerator.next());
        when(stockBatchRepository.findById(88L)).thenReturn(Optional.of(batch));

        service.postInternalConsumption(req(new BigDecimal("2"), ConsumptionCategory.CANTEEN, AUTHORISER_ID, 88L));

        // Batch should be EXHAUSTED — a subsequent recall writes no extra write-off
        assertThat(batch.getQtyOnHand()).isEqualByComparingTo("0");
        assertThat(batch.getStatus()).isEqualTo(com.orbix.engine.modules.stock.domain.enums.StockBatchStatus.EXHAUSTED);
        // Only the consumption move was posted — no EXPIRY_WRITE_OFF
        ArgumentCaptor<PostStockMoveRequestDto> captor = ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(captor.capture());
        assertThat(captor.getValue().moveType()).isEqualTo(StockMoveType.INTERNAL_CONSUMPTION);
    }

    @Test
    void post_explicitBatchId_wrongItem_isRejected() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(batchItem));

        StockBatch foreignBatch = new StockBatch(9999L, BRANCH_ID, COMPANY_ID, "B-FOREIGN",
            null, null, new BigDecimal("5"), new BigDecimal("100"), "GRN", 1L, ACTOR_ID);
        foreignBatch.setId(99L);
        ReflectionTestUtils.setField(foreignBatch, "uid", UidGenerator.next());
        when(stockBatchRepository.findById(99L)).thenReturn(Optional.of(foreignBatch));

        assertThatThrownBy(() -> service.postInternalConsumption(
                req(new BigDecimal("2"), ConsumptionCategory.CANTEEN, AUTHORISER_ID, 99L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not belong to item");
        verify(stockMoveService, never()).post(any());
    }
}

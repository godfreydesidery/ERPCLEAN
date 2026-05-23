package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.stock.domain.dto.BatchPickDto;
import com.orbix.engine.modules.stock.domain.dto.CreateStockBatchRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.RecallStockBatchRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockBatchDto;
import com.orbix.engine.modules.stock.domain.entity.StockBatch;
import com.orbix.engine.modules.stock.domain.enums.StockBatchStatus;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.repository.StockBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockBatchServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long ITEM_ID = 8801L;
    private static final Long ACTOR_ID = 4L;

    @Mock private StockBatchRepository batches;
    @Mock private StockMoveService stockMoveService;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;

    @InjectMocks private StockBatchServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(batches.save(any(StockBatch.class))).thenAnswer(inv -> {
            StockBatch b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(100L);
            }
            return b;
        });
    }

    private static StockBatch batch(Long id, String no, LocalDate expiry, BigDecimal qty, BigDecimal cost) {
        StockBatch b = new StockBatch(ITEM_ID, BRANCH_ID, COMPANY_ID, no, null, expiry, qty, cost,
            "GRN", 999L, ACTOR_ID);
        b.setId(id);
        return b;
    }

    @Test
    void createBatch_persistsAndPublishesCreatedEvent() {
        CreateStockBatchRequestDto request = new CreateStockBatchRequestDto(
            ITEM_ID, BRANCH_ID, "B-001", null, LocalDate.of(2026, 12, 31),
            new BigDecimal("50"), new BigDecimal("120"), "GRN", 999L);
        when(batches.findByBranchIdAndItemIdAndBatchNo(BRANCH_ID, ITEM_ID, "B-001"))
            .thenReturn(Optional.empty());

        StockBatchDto created = service.createBatch(request);

        assertThat(created.batchNo()).isEqualTo("B-001");
        assertThat(created.qtyOnHand()).isEqualByComparingTo("50");
        assertThat(created.status()).isEqualTo(StockBatchStatus.ACTIVE);
        verify(events).publish(eq("StockBatchCreated.v1"), any(), any(), any());
    }

    @Test
    void createBatch_rejectsDuplicateOnSameBranchItemNo() {
        when(batches.findByBranchIdAndItemIdAndBatchNo(BRANCH_ID, ITEM_ID, "B-001"))
            .thenReturn(Optional.of(batch(1L, "B-001", LocalDate.now(), BigDecimal.ONE, BigDecimal.ONE)));

        assertThatThrownBy(() -> service.createBatch(new CreateStockBatchRequestDto(
                ITEM_ID, BRANCH_ID, "B-001", null, null, BigDecimal.ONE, BigDecimal.ONE, "GRN", 1L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(batches, never()).save(any());
    }

    @Test
    void drainFefo_picksEarliestExpiryFirstAndExhaustsBatches() {
        StockBatch b1 = batch(1L, "B-OLD", LocalDate.of(2026, 6, 1), new BigDecimal("4"), new BigDecimal("100"));
        StockBatch b2 = batch(2L, "B-MID", LocalDate.of(2026, 7, 1), new BigDecimal("5"), new BigDecimal("110"));
        StockBatch b3 = batch(3L, "B-NEW", LocalDate.of(2026, 8, 1), new BigDecimal("5"), new BigDecimal("120"));
        when(batches.findByItemIdAndBranchIdAndStatusOrderByExpiryAtAscIdAsc(
                ITEM_ID, BRANCH_ID, StockBatchStatus.ACTIVE))
            .thenReturn(List.of(b1, b2, b3));

        List<BatchPickDto> picks = service.drainFefo(ITEM_ID, BRANCH_ID, new BigDecimal("7"));

        assertThat(picks).hasSize(2);
        assertThat(picks.get(0).batchNo()).isEqualTo("B-OLD");
        assertThat(picks.get(0).qty()).isEqualByComparingTo("4");
        assertThat(picks.get(0).cost()).isEqualByComparingTo("100");
        assertThat(picks.get(1).batchNo()).isEqualTo("B-MID");
        assertThat(picks.get(1).qty()).isEqualByComparingTo("3");
        assertThat(picks.get(1).cost()).isEqualByComparingTo("110");
        assertThat(b1.getStatus()).isEqualTo(StockBatchStatus.EXHAUSTED);
        assertThat(b1.getQtyOnHand()).isEqualByComparingTo("0");
        assertThat(b2.getQtyOnHand()).isEqualByComparingTo("2");
        assertThat(b2.getStatus()).isEqualTo(StockBatchStatus.ACTIVE);
        assertThat(b3.getQtyOnHand()).isEqualByComparingTo("5");
        verify(events).publish(eq("StockBatchExhausted.v1"), any(), any(), any());
    }

    @Test
    void drainFefo_throwsWhenActiveBatchesAreInsufficient() {
        StockBatch b1 = batch(1L, "B-OLD", LocalDate.of(2026, 6, 1), new BigDecimal("3"), new BigDecimal("100"));
        when(batches.findByItemIdAndBranchIdAndStatusOrderByExpiryAtAscIdAsc(
                ITEM_ID, BRANCH_ID, StockBatchStatus.ACTIVE))
            .thenReturn(List.of(b1));

        BigDecimal demand = new BigDecimal("5");
        assertThatThrownBy(() -> service.drainFefo(ITEM_ID, BRANCH_ID, demand))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Insufficient active batches");
    }

    @Test
    void drainFefo_rejectsNonPositiveQty() {
        BigDecimal zero = BigDecimal.ZERO;
        assertThatThrownBy(() -> service.drainFefo(ITEM_ID, BRANCH_ID, zero))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markExpired_flipsActiveBatchesPastExpiryAndEmitsEvent() {
        LocalDate today = LocalDate.of(2026, 5, 15);
        StockBatch expired = batch(1L, "B-OLD", LocalDate.of(2026, 5, 1), new BigDecimal("3"), new BigDecimal("100"));
        when(batches.findByStatusAndExpiryAtBefore(StockBatchStatus.ACTIVE, today))
            .thenReturn(List.of(expired));

        int flipped = service.markExpired(today);

        assertThat(flipped).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo(StockBatchStatus.EXPIRED);
        verify(events).publish(eq("StockBatchExpired.v1"), any(), any(), any());
    }

    @Test
    void recallBatch_writesOffRemainingAndEmitsRecalledEvent() {
        StockBatch batch = batch(42L, "B-RECALL", LocalDate.of(2026, 9, 1),
            new BigDecimal("8"), new BigDecimal("125"));
        when(batches.findById(42L)).thenReturn(Optional.of(batch));

        StockBatchDto result = service.recallBatch(42L,
            new RecallStockBatchRequestDto("Supplier safety notice"));

        assertThat(result.status()).isEqualTo(StockBatchStatus.RECALLED);
        assertThat(batch.getQtyOnHand()).isEqualByComparingTo("0");

        ArgumentCaptor<PostStockMoveRequestDto> postCaptor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(postCaptor.capture());
        PostStockMoveRequestDto post = postCaptor.getValue();
        assertThat(post.qty()).isEqualByComparingTo("-8");
        assertThat(post.moveType()).isEqualTo(StockMoveType.EXPIRY_WRITE_OFF);
        assertThat(post.batchId()).isEqualTo(42L);
        verify(events).publish(eq("BatchRecalled.v1"), any(), any(), any());
    }

    @Test
    void recallBatch_withZeroOnHand_skipsWriteOff() {
        StockBatch batch = batch(43L, "B-EMPTY", LocalDate.of(2026, 9, 1),
            new BigDecimal("5"), new BigDecimal("100"));
        batch.setQtyOnHand(BigDecimal.ZERO);
        when(batches.findById(43L)).thenReturn(Optional.of(batch));

        service.recallBatch(43L, new RecallStockBatchRequestDto("admin error"));

        verify(stockMoveService, never()).post(any());
        verify(events).publish(eq("BatchRecalled.v1"), any(), any(), any());
    }

    @Test
    void recallBatch_fromAnotherCompany_throwsNotFound() {
        StockBatch foreign = new StockBatch(ITEM_ID, BRANCH_ID, 999L, "B-X", null, null,
            BigDecimal.ONE, BigDecimal.ONE, "GRN", 1L, ACTOR_ID);
        foreign.setId(99L);
        when(batches.findById(99L)).thenReturn(Optional.of(foreign));

        RecallStockBatchRequestDto dto = new RecallStockBatchRequestDto("recall");
        assertThatThrownBy(() -> service.recallBatch(99L, dto))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void listExpiringSoon_filtersByCompanyAndCutoff() {
        LocalDate today = LocalDate.now();
        StockBatch soon = batch(11L, "B-NEAR", today.plusDays(2), new BigDecimal("5"), new BigDecimal("100"));
        when(branchScope.requireReadable(null)).thenReturn(null);  // company-wide caller: no branch filter
        when(batches.findByCompanyIdAndStatusAndExpiryAtBeforeOrderByExpiryAtAscIdAsc(
                eq(COMPANY_ID), eq(StockBatchStatus.ACTIVE), any(LocalDate.class)))
            .thenReturn(List.of(soon));

        List<StockBatchDto> result = service.listExpiringSoon(null, 7);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).batchNo()).isEqualTo("B-NEAR");
    }
}

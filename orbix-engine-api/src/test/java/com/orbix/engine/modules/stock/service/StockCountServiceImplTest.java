package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.stock.domain.dto.CreateStockCountRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.entity.StockCount;
import com.orbix.engine.modules.stock.domain.entity.StockCountLine;
import com.orbix.engine.modules.stock.domain.enums.StockCountStatus;
import com.orbix.engine.modules.stock.domain.enums.StockCountType;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.repository.StockCountLineRepository;
import com.orbix.engine.modules.stock.repository.StockCountRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockCountServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long ACTOR_ID = 4L;

    @Mock private StockCountRepository counts;
    @Mock private StockCountLineRepository countLines;
    @Mock private ItemBranchBalanceRepository balances;
    @Mock private StockMoveService stockMoveService;
    @Mock private RequestContext context;

    @InjectMocks private StockCountServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(counts.save(any(StockCount.class))).thenAnswer(inv -> {
            StockCount c = inv.getArgument(0);
            if (c.getId() == null) c.setId(1L);
            return c;
        });
        lenient().when(countLines.save(any(StockCountLine.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private StockCount count(StockCountStatus status) {
        StockCount c = new StockCount("SC-1", BRANCH_ID, COMPANY_ID, LocalDate.of(2026, 5, 14),
            StockCountType.CYCLE, ACTOR_ID);
        c.setId(1L);
        c.setStatus(status);
        return c;
    }

    @Test
    void createCount_freezesSystemQtyFromBalance() {
        when(counts.existsByBranchIdAndNumber(BRANCH_ID, "SC-1")).thenReturn(false);
        ItemBranchBalance bal = new ItemBranchBalance(8801L, BRANCH_ID);
        bal.setQtyOnHand(new BigDecimal("25"));
        when(balances.findById(new ItemBranchBalanceId(8801L, BRANCH_ID))).thenReturn(Optional.of(bal));
        when(countLines.findByStockCountId(1L)).thenReturn(List.of());

        service.createCount(new CreateStockCountRequestDto(
            "SC-1", BRANCH_ID, LocalDate.of(2026, 5, 14), StockCountType.CYCLE, List.of(8801L)));

        ArgumentCaptor<StockCountLine> line = ArgumentCaptor.forClass(StockCountLine.class);
        verify(countLines).save(line.capture());
        assertThat(line.getValue().getSystemQty()).isEqualByComparingTo("25");
    }

    @Test
    void createCount_rejectsDuplicateNumber() {
        when(counts.existsByBranchIdAndNumber(BRANCH_ID, "SC-1")).thenReturn(true);

        assertThatThrownBy(() -> service.createCount(new CreateStockCountRequestDto(
            "SC-1", BRANCH_ID, LocalDate.of(2026, 5, 14), StockCountType.CYCLE, List.of(8801L))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void closeCount_computesVariancePerLine() {
        StockCount c = count(StockCountStatus.IN_PROGRESS);
        StockCountLine l = new StockCountLine(1L, 8801L, new BigDecimal("25"));
        l.setId(10L);
        l.recordCount(new BigDecimal("22"), "short");
        when(counts.findById(1L)).thenReturn(Optional.of(c));
        when(countLines.findByStockCountId(1L)).thenReturn(List.of(l));

        service.closeCount(1L);

        assertThat(c.getStatus()).isEqualTo(StockCountStatus.CLOSED);
        assertThat(l.getVarianceQty()).isEqualByComparingTo("-3");
    }

    @Test
    void postCount_postsAdjustmentMoveForNonZeroVarianceOnly() {
        StockCount c = count(StockCountStatus.CLOSED);
        StockCountLine varied = new StockCountLine(1L, 8801L, new BigDecimal("25"));
        varied.setId(10L);
        varied.setVarianceQty(new BigDecimal("-3"));
        StockCountLine flat = new StockCountLine(1L, 8802L, new BigDecimal("10"));
        flat.setId(11L);
        flat.setVarianceQty(BigDecimal.ZERO);
        when(counts.findById(1L)).thenReturn(Optional.of(c));
        when(countLines.findByStockCountId(1L)).thenReturn(List.of(varied, flat));
        when(balances.findById(any())).thenReturn(Optional.empty());

        service.postCount(1L);

        assertThat(c.getStatus()).isEqualTo(StockCountStatus.POSTED);
        ArgumentCaptor<PostStockMoveRequestDto> move = ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(move.capture());
        assertThat(move.getValue().itemId()).isEqualTo(8801L);
        assertThat(move.getValue().qty()).isEqualByComparingTo("-3");
        assertThat(move.getValue().moveType()).isEqualTo(StockMoveType.ADJUSTMENT);
    }

    @Test
    void getCount_fromAnotherCompany_throwsNotFound() {
        StockCount foreign = new StockCount("SC-9", BRANCH_ID, 999L, LocalDate.of(2026, 5, 14),
            StockCountType.SPOT, ACTOR_ID);
        foreign.setId(9L);
        when(counts.findById(9L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getCount(9L)).isInstanceOf(NoSuchElementException.class);
        verify(stockMoveService, never()).post(any());
    }
}

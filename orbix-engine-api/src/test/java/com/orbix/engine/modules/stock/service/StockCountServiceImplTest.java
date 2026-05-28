package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.SettingsService;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.iam.domain.enums.Permissions;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.stock.domain.dto.CreateStockCountRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockCountRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockMoveDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.entity.StockCount;
import com.orbix.engine.modules.stock.domain.entity.StockCountLine;
import com.orbix.engine.modules.stock.domain.enums.StockCountStatus;
import com.orbix.engine.modules.stock.domain.enums.StockCountType;
import com.orbix.engine.modules.stock.domain.enums.StockMoveDirection;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockCountServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long ACTOR_ID = 4L;
    private static final Long AUTHORISER_ID = 9L;

    @Mock private StockCountRepository counts;
    @Mock private StockCountLineRepository countLines;
    @Mock private ItemBranchBalanceRepository balances;
    @Mock private StockMoveService stockMoveService;
    @Mock private PermissionResolverService permissions;
    @Mock private SettingsService settings;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;

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
        lenient().when(settings.getDecimal(SettingKey.STOCK_ADJUSTMENT_THRESHOLD))
            .thenReturn(new BigDecimal("50000"));
        lenient().when(stockMoveService.post(any())).thenAnswer(inv -> {
            PostStockMoveRequestDto m = inv.getArgument(0);
            return new StockMoveDto(1L, Instant.EPOCH, m.itemId(), m.branchId(), COMPANY_ID,
                m.qty(), m.unitCost() != null ? m.unitCost() : BigDecimal.ZERO,
                m.qty().signum() >= 0 ? StockMoveDirection.IN : StockMoveDirection.OUT,
                m.moveType(), m.refType(), m.refId(), ACTOR_ID, m.notes(),
                m.batchId(), m.sectionId(), m.consumptionCategory(), m.authorisedByUserId(),
                null, null);
        });
    }

    private StockCount count(StockCountStatus status) {
        StockCount c = new StockCount("SC-1", BRANCH_ID, COMPANY_ID, LocalDate.of(2026, 5, 14),
            StockCountType.CYCLE, ACTOR_ID);
        c.setId(1L);
        ReflectionTestUtils.setField(c, "uid", UidGenerator.next());
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

        CreateStockCountRequestDto request = new CreateStockCountRequestDto(
            "SC-1", BRANCH_ID, LocalDate.of(2026, 5, 14), StockCountType.CYCLE, List.of(8801L));
        assertThatThrownBy(() -> service.createCount(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void startCount_emitsStockCountStartedEvent() {
        StockCount c = count(StockCountStatus.DRAFT);
        when(counts.findByUid(c.getUid())).thenReturn(Optional.of(c));
        when(countLines.findByStockCountId(1L)).thenReturn(List.of());

        service.startCount(c.getUid());

        assertThat(c.getStatus()).isEqualTo(StockCountStatus.IN_PROGRESS);
        verify(events).publish(eq("StockCountStarted.v1"), eq("StockCount"), any(), any());
    }

    @Test
    void closeCount_computesVariancePerLineAndEmitsClosedEvent() {
        StockCount c = count(StockCountStatus.IN_PROGRESS);
        StockCountLine l = new StockCountLine(1L, 8801L, new BigDecimal("25"));
        l.setId(10L);
        l.recordCount(new BigDecimal("22"), "short");
        when(counts.findByUid(c.getUid())).thenReturn(Optional.of(c));
        when(countLines.findByStockCountId(1L)).thenReturn(List.of(l));

        service.closeCount(c.getUid());

        assertThat(c.getStatus()).isEqualTo(StockCountStatus.CLOSED);
        assertThat(l.getVarianceQty()).isEqualByComparingTo("-3");
        verify(events).publish(eq("StockCountClosed.v1"), eq("StockCount"), any(), any());
    }

    @Test
    void postCount_underThreshold_solo_succeeds() {
        StockCount c = count(StockCountStatus.CLOSED);
        StockCountLine varied = new StockCountLine(1L, 8801L, new BigDecimal("25"));
        varied.setId(10L);
        varied.setVarianceQty(new BigDecimal("-3"));
        StockCountLine flat = new StockCountLine(1L, 8802L, new BigDecimal("10"));
        flat.setId(11L);
        flat.setVarianceQty(BigDecimal.ZERO);
        when(counts.findByUid(c.getUid())).thenReturn(Optional.of(c));
        when(countLines.findByStockCountId(1L)).thenReturn(List.of(varied, flat));
        ItemBranchBalance balVaried = new ItemBranchBalance(8801L, BRANCH_ID);
        balVaried.setAvgCost(new BigDecimal("100"));  // 3 * 100 = 300 (well under threshold)
        when(balances.findById(new ItemBranchBalanceId(8801L, BRANCH_ID)))
            .thenReturn(Optional.of(balVaried));

        service.postCount(c.getUid(), PostStockCountRequestDto.empty());

        assertThat(c.getStatus()).isEqualTo(StockCountStatus.POSTED);
        ArgumentCaptor<PostStockMoveRequestDto> move = ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(move.capture());
        assertThat(move.getValue().itemId()).isEqualTo(8801L);
        assertThat(move.getValue().qty()).isEqualByComparingTo("-3");
        assertThat(move.getValue().moveType()).isEqualTo(StockMoveType.ADJUSTMENT);
        verify(events).publish(eq("StockCountPosted.v1"), eq("StockCount"), any(), any());
    }

    @Test
    void postCount_aboveThreshold_withoutAuthoriser_isRejected() {
        StockCount c = count(StockCountStatus.CLOSED);
        StockCountLine varied = aboveThresholdVarianceLine();
        when(counts.findByUid(c.getUid())).thenReturn(Optional.of(c));
        when(countLines.findByStockCountId(1L)).thenReturn(List.of(varied));
        ItemBranchBalance bal = new ItemBranchBalance(8801L, BRANCH_ID);
        bal.setAvgCost(new BigDecimal("1000"));  // 100 * 1000 = 100,000 > 50k
        when(balances.findById(new ItemBranchBalanceId(8801L, BRANCH_ID))).thenReturn(Optional.of(bal));

        PostStockCountRequestDto request = PostStockCountRequestDto.empty();
        String uid = c.getUid();
        assertThatThrownBy(() -> service.postCount(uid, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(Permissions.STOCK_COUNT_APPROVE);
        verify(stockMoveService, never()).post(any());
        verify(events, never()).publish(eq("StockCountPosted.v1"), any(), any(), any());
    }

    @Test
    void postCount_aboveThreshold_withSelfAuthoriser_isRejected() {
        StockCount c = count(StockCountStatus.CLOSED);
        StockCountLine varied = aboveThresholdVarianceLine();
        when(counts.findByUid(c.getUid())).thenReturn(Optional.of(c));
        when(countLines.findByStockCountId(1L)).thenReturn(List.of(varied));
        ItemBranchBalance bal = new ItemBranchBalance(8801L, BRANCH_ID);
        bal.setAvgCost(new BigDecimal("1000"));
        when(balances.findById(new ItemBranchBalanceId(8801L, BRANCH_ID))).thenReturn(Optional.of(bal));

        PostStockCountRequestDto request = new PostStockCountRequestDto(ACTOR_ID);
        String uid = c.getUid();
        assertThatThrownBy(() -> service.postCount(uid, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("your own");
    }

    @Test
    void postCount_aboveThreshold_authoriserMissingPermission_403() {
        StockCount c = count(StockCountStatus.CLOSED);
        StockCountLine varied = aboveThresholdVarianceLine();
        when(counts.findByUid(c.getUid())).thenReturn(Optional.of(c));
        when(countLines.findByStockCountId(1L)).thenReturn(List.of(varied));
        ItemBranchBalance bal = new ItemBranchBalance(8801L, BRANCH_ID);
        bal.setAvgCost(new BigDecimal("1000"));
        when(balances.findById(new ItemBranchBalanceId(8801L, BRANCH_ID))).thenReturn(Optional.of(bal));
        when(permissions.resolve(AUTHORISER_ID, COMPANY_ID, null)).thenReturn(Set.of());

        PostStockCountRequestDto request = new PostStockCountRequestDto(AUTHORISER_ID);
        String uid = c.getUid();
        assertThatThrownBy(() -> service.postCount(uid, request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining(Permissions.STOCK_COUNT_APPROVE);
    }

    @Test
    void postCount_aboveThreshold_withApprovedAuthoriser_succeeds() {
        StockCount c = count(StockCountStatus.CLOSED);
        StockCountLine varied = aboveThresholdVarianceLine();
        when(counts.findByUid(c.getUid())).thenReturn(Optional.of(c));
        when(countLines.findByStockCountId(1L)).thenReturn(List.of(varied));
        ItemBranchBalance bal = new ItemBranchBalance(8801L, BRANCH_ID);
        bal.setAvgCost(new BigDecimal("1000"));
        when(balances.findById(new ItemBranchBalanceId(8801L, BRANCH_ID))).thenReturn(Optional.of(bal));
        when(permissions.resolve(AUTHORISER_ID, COMPANY_ID, null))
            .thenReturn(Set.of(Permissions.STOCK_COUNT_APPROVE));

        service.postCount(c.getUid(), new PostStockCountRequestDto(AUTHORISER_ID));

        assertThat(c.getStatus()).isEqualTo(StockCountStatus.POSTED);
        verify(stockMoveService).post(any());
        verify(events).publish(eq("StockCountPosted.v1"), eq("StockCount"), any(), any());
    }

    @Test
    void getCount_fromAnotherCompany_throwsNotFound() {
        StockCount foreign = new StockCount("SC-9", BRANCH_ID, 999L, LocalDate.of(2026, 5, 14),
            StockCountType.SPOT, ACTOR_ID);
        foreign.setId(9L);
        ReflectionTestUtils.setField(foreign, "uid", UidGenerator.next());
        when(counts.findByUid(foreign.getUid())).thenReturn(Optional.of(foreign));

        String uid = foreign.getUid();
        assertThatThrownBy(() -> service.getCount(uid)).isInstanceOf(NoSuchElementException.class);
        verify(stockMoveService, never()).post(any());
    }

    private StockCountLine aboveThresholdVarianceLine() {
        StockCountLine varied = new StockCountLine(1L, 8801L, new BigDecimal("100"));
        varied.setId(10L);
        varied.setVarianceQty(new BigDecimal("-100"));  // value will be 100 * avgCost
        return varied;
    }
}

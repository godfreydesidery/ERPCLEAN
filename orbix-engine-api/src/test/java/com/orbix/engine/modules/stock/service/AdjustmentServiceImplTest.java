package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.stock.domain.dto.PostAdjustmentRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdjustmentServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long ITEM_ID = 8801L;
    private static final Long ACTOR_ID = 4L;
    private static final Long AUTHORISER_ID = 9L;

    @Mock private StockMoveService stockMoveService;
    @Mock private ItemBranchBalanceRepository balances;
    @Mock private PermissionResolverService permissions;
    @Mock private RequestContext context;

    @InjectMocks private AdjustmentServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        ReflectionTestUtils.setField(service, "threshold", new BigDecimal("50000"));
    }

    private static PostAdjustmentRequestDto req(BigDecimal qty, BigDecimal unitCost,
                                                Long authoriserId, boolean oversell) {
        return new PostAdjustmentRequestDto(ITEM_ID, BRANCH_ID, qty, unitCost,
            "physical recount", null, null, authoriserId, oversell);
    }

    @Test
    void post_belowThreshold_solo_succeeds() {
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
            .thenReturn(Optional.of(balance(new BigDecimal("100"))));

        service.postAdjustment(req(new BigDecimal("-3"), null, null, false));

        ArgumentCaptor<PostStockMoveRequestDto> captor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(captor.capture());
        PostStockMoveRequestDto posted = captor.getValue();
        assert posted.moveType() == StockMoveType.ADJUSTMENT;
        assert posted.authorisedByUserId() == null;
    }

    @Test
    void post_aboveThreshold_withoutAuthoriser_isRejected() {
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
            .thenReturn(Optional.of(balance(new BigDecimal("1000"))));

        // -100 * 1000 = 100,000 > threshold 50,000
        PostAdjustmentRequestDto request = req(new BigDecimal("-100"), null, null, false);
        assertThatThrownBy(() -> service.postAdjustment(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("authoriser");
        verify(stockMoveService, never()).post(any());
    }

    @Test
    void post_aboveThreshold_withSelfAuthoriser_isRejected() {
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
            .thenReturn(Optional.of(balance(new BigDecimal("1000"))));

        PostAdjustmentRequestDto request = req(new BigDecimal("-100"), null, ACTOR_ID, false);
        assertThatThrownBy(() -> service.postAdjustment(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("your own");
    }

    @Test
    void post_aboveThreshold_authoriserMissingPermission_403() {
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
            .thenReturn(Optional.of(balance(new BigDecimal("1000"))));
        when(permissions.resolve(AUTHORISER_ID, COMPANY_ID, null)).thenReturn(Set.of());

        PostAdjustmentRequestDto request = req(new BigDecimal("-100"), null, AUTHORISER_ID, false);
        assertThatThrownBy(() -> service.postAdjustment(request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining(AdjustmentServiceImpl.APPROVE_PERMISSION);
    }

    @Test
    void post_aboveThreshold_withApprovedAuthoriser_succeeds() {
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
            .thenReturn(Optional.of(balance(new BigDecimal("1000"))));
        when(permissions.resolve(AUTHORISER_ID, COMPANY_ID, null))
            .thenReturn(Set.of(AdjustmentServiceImpl.APPROVE_PERMISSION));

        service.postAdjustment(req(new BigDecimal("-100"), null, AUTHORISER_ID, false));

        ArgumentCaptor<PostStockMoveRequestDto> captor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(captor.capture());
        assert captor.getValue().authorisedByUserId().equals(AUTHORISER_ID);
    }

    @Test
    void post_oversell_evenBelowThreshold_requiresAuthoriser() {
        when(balances.findById(new ItemBranchBalanceId(ITEM_ID, BRANCH_ID)))
            .thenReturn(Optional.of(balance(new BigDecimal("10"))));

        // -2 * 10 = 20 (below 50k threshold); but oversell=true forces authoriser
        PostAdjustmentRequestDto request = req(new BigDecimal("-2"), null, null, true);
        assertThatThrownBy(() -> service.postAdjustment(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("authoriser");
    }

    @Test
    void post_zeroQty_isRejected() {
        PostAdjustmentRequestDto request = req(BigDecimal.ZERO, null, null, false);
        assertThatThrownBy(() -> service.postAdjustment(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-zero");
    }

    @Test
    void post_inboundUsesUnitCostForThreshold() {
        // +1000 * 60 unit cost = 60,000 > threshold; no authoriser → reject
        PostAdjustmentRequestDto request = req(new BigDecimal("1000"), new BigDecimal("60"), null, false);
        assertThatThrownBy(() -> service.postAdjustment(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("authoriser");
    }

    private ItemBranchBalance balance(BigDecimal avgCost) {
        ItemBranchBalance b = new ItemBranchBalance(ITEM_ID, BRANCH_ID);
        b.setQtyOnHand(new BigDecimal("500"));
        b.setAvgCost(avgCost);
        return b;
    }
}

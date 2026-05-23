package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.stock.domain.dto.PostInternalConsumptionRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.enums.ConsumptionCategory;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    @Mock private PermissionResolverService permissions;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;

    @InjectMocks private InternalConsumptionServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    private static PostInternalConsumptionRequestDto req(BigDecimal qty,
                                                         ConsumptionCategory category,
                                                         Long authoriserId) {
        return new PostInternalConsumptionRequestDto(ITEM_ID, BRANCH_ID, qty, category, SECTION_ID,
            authoriserId, "staff lunch", null);
    }

    @Test
    void post_emitsOutboundInternalConsumptionMoveWithCategoryAndAuthoriser() {
        when(permissions.resolve(AUTHORISER_ID, COMPANY_ID, null))
            .thenReturn(Set.of(InternalConsumptionServiceImpl.AUTHORISER_PERMISSION));

        service.postInternalConsumption(req(new BigDecimal("5"), ConsumptionCategory.CANTEEN, AUTHORISER_ID));

        ArgumentCaptor<PostStockMoveRequestDto> captor =
            ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(captor.capture());
        PostStockMoveRequestDto posted = captor.getValue();
        assertThat(posted.qty()).isEqualByComparingTo("-5");
        assertThat(posted.moveType()).isEqualTo(StockMoveType.INTERNAL_CONSUMPTION);
        assertThat(posted.consumptionCategory()).isEqualTo(ConsumptionCategory.CANTEEN);
        assertThat(posted.sectionId()).isEqualTo(SECTION_ID);
        assertThat(posted.authorisedByUserId()).isEqualTo(AUTHORISER_ID);
    }

    @Test
    void post_authoriserIsCaller_isRejected() {
        PostInternalConsumptionRequestDto request =
            req(new BigDecimal("5"), ConsumptionCategory.CANTEEN, ACTOR_ID);
        assertThatThrownBy(() -> service.postInternalConsumption(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("your own");
        verify(stockMoveService, never()).post(any());
    }

    @Test
    void post_authoriserWithoutPermission_403() {
        when(permissions.resolve(AUTHORISER_ID, COMPANY_ID, null)).thenReturn(Set.of());

        PostInternalConsumptionRequestDto request =
            req(new BigDecimal("5"), ConsumptionCategory.DISPLAY, AUTHORISER_ID);
        assertThatThrownBy(() -> service.postInternalConsumption(request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining(InternalConsumptionServiceImpl.AUTHORISER_PERMISSION);
        verify(stockMoveService, never()).post(any());
    }
}

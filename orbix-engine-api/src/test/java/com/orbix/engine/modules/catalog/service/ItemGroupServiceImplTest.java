package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemGroupDto;
import com.orbix.engine.modules.catalog.domain.dto.MoveItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.entity.ItemGroup;
import com.orbix.engine.modules.catalog.repository.ItemGroupRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class ItemGroupServiceImplTest {

    private static final Long COMPANY_ID = 2L;
    private static final Long ACTOR_ID = 5L;

    @Mock private ItemGroupRepository groups;
    @Mock private RequestContext context;

    @InjectMocks private ItemGroupServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    private static ItemGroup group(Long id, Long parentId, int level, String code) {
        ItemGroup g = new ItemGroup(COMPANY_ID, parentId, level, code, "Name " + code, ACTOR_ID);
        g.setId(id);
        return g;
    }

    @Test
    void createGroup_atRoot_isLevelOne() {
        when(groups.existsByCompanyIdAndCode(COMPANY_ID, "FOOD")).thenReturn(false);
        when(groups.save(any(ItemGroup.class))).thenAnswer(inv -> {
            ItemGroup g = inv.getArgument(0);
            g.setId(1L);
            return g;
        });

        ItemGroupDto result = service.createGroup(new CreateItemGroupRequestDto(null, "food", "Food"));

        assertThat(result.level()).isEqualTo(1);
        assertThat(result.code()).isEqualTo("FOOD");
        assertThat(result.parentId()).isNull();
    }

    @Test
    void createGroup_underParent_isParentLevelPlusOne() {
        when(groups.existsByCompanyIdAndCode(COMPANY_ID, "DAIRY")).thenReturn(false);
        when(groups.findById(1L)).thenReturn(Optional.of(group(1L, null, 1, "FOOD")));
        when(groups.save(any(ItemGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemGroupDto result = service.createGroup(new CreateItemGroupRequestDto(1L, "DAIRY", "Dairy"));

        assertThat(result.level()).isEqualTo(2);
        assertThat(result.parentId()).isEqualTo(1L);
    }

    @Test
    void createGroup_rejectsDuplicateCode() {
        when(groups.existsByCompanyIdAndCode(COMPANY_ID, "FOOD")).thenReturn(true);

        assertThatThrownBy(() -> service.createGroup(new CreateItemGroupRequestDto(null, "FOOD", "Food")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(groups, never()).save(any());
    }

    @Test
    void renameGroup_updatesName() {
        ItemGroup existing = group(1L, null, 1, "FOOD");
        when(groups.findById(1L)).thenReturn(Optional.of(existing));

        ItemGroupDto result = service.renameGroup(1L, new UpdateItemGroupRequestDto("Groceries"));

        assertThat(result.name()).isEqualTo("Groceries");
        assertThat(existing.getName()).isEqualTo("Groceries");
    }

    @Test
    void moveGroup_underNewParent_recomputesSubtreeLevels() {
        // food(1,L1) -> dairy(2,L2) -> milk(3,L3);  beverages(4,L1)
        ItemGroup food = group(1L, null, 1, "FOOD");
        ItemGroup dairy = group(2L, 1L, 2, "DAIRY");
        ItemGroup milk = group(3L, 2L, 3, "MILK");
        ItemGroup beverages = group(4L, null, 1, "BEV");
        when(groups.findById(2L)).thenReturn(Optional.of(dairy));
        when(groups.findById(4L)).thenReturn(Optional.of(beverages));
        when(groups.findByCompanyId(COMPANY_ID)).thenReturn(List.of(food, dairy, milk, beverages));

        // move dairy under beverages: dairy L2->L2 (beverages.L1+1), milk follows
        service.moveGroup(2L, new MoveItemGroupRequestDto(4L));

        assertThat(dairy.getParentId()).isEqualTo(4L);
        assertThat(dairy.getLevel()).isEqualTo(2);
        assertThat(milk.getLevel()).isEqualTo(3);

        // move dairy to root: L2->L1 (delta -1), milk L3->L2
        when(groups.findByCompanyId(COMPANY_ID)).thenReturn(List.of(food, dairy, milk, beverages));
        service.moveGroup(2L, new MoveItemGroupRequestDto(null));
        assertThat(dairy.getParentId()).isNull();
        assertThat(dairy.getLevel()).isEqualTo(1);
        assertThat(milk.getLevel()).isEqualTo(2);
    }

    @Test
    void moveGroup_rejectsMovingUnderOwnDescendant() {
        ItemGroup food = group(1L, null, 1, "FOOD");
        ItemGroup dairy = group(2L, 1L, 2, "DAIRY");
        when(groups.findById(1L)).thenReturn(Optional.of(food));
        when(groups.findById(2L)).thenReturn(Optional.of(dairy));
        when(groups.findByCompanyId(COMPANY_ID)).thenReturn(List.of(food, dairy));

        assertThatThrownBy(() -> service.moveGroup(1L, new MoveItemGroupRequestDto(2L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("itself or its descendant");
    }

    @Test
    void archiveGroup_setsArchivedStatus() {
        ItemGroup existing = group(1L, null, 1, "FOOD");
        when(groups.findById(1L)).thenReturn(Optional.of(existing));

        service.archiveGroup(1L);

        assertThat(existing.getStatus().name()).isEqualTo("ARCHIVED");
    }

    @Test
    void requireGroup_notFound_throwsNoSuchElement() {
        when(groups.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renameGroup(404L, new UpdateItemGroupRequestDto("x")))
            .isInstanceOf(NoSuchElementException.class);
    }
}

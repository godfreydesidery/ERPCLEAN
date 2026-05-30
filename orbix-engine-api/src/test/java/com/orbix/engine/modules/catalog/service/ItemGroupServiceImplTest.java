package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemGroupDto;
import com.orbix.engine.modules.catalog.domain.dto.MoveItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.entity.ItemGroup;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.repository.ItemGroupRepository;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
    @Mock private ItemRepository items;
    @Mock private RequestContext context;

    @InjectMocks private ItemGroupServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    /** Build an ItemGroup fixture with both id and uid populated (bypassing @PrePersist). */
    private static ItemGroup group(Long id, Long parentId, int level, String code) {
        ItemGroup g = new ItemGroup(COMPANY_ID, parentId, level, code, "Name " + code, ACTOR_ID);
        g.setId(id);
        ReflectionTestUtils.setField(g, "uid", UidGenerator.next());
        return g;
    }

    @Test
    void createGroup_atRoot_isLevelOne() {
        when(groups.existsByCompanyIdAndCode(COMPANY_ID, "FOOD")).thenReturn(false);
        when(groups.save(any(ItemGroup.class))).thenAnswer(inv -> {
            ItemGroup g = inv.getArgument(0);
            g.setId(1L);
            ReflectionTestUtils.setField(g, "uid", UidGenerator.next());
            return g;
        });

        ItemGroupDto result = service.createGroup(new CreateItemGroupRequestDto(null, "food", "Food"));

        assertThat(result.level()).isEqualTo(1);
        assertThat(result.code()).isEqualTo("FOOD");
        assertThat(result.parentId()).isNull();
        assertThat(result.uid()).isNotBlank();
    }

    @Test
    void createGroup_underParent_isParentLevelPlusOne() {
        when(groups.existsByCompanyIdAndCode(COMPANY_ID, "DAIRY")).thenReturn(false);
        when(groups.findById(1L)).thenReturn(Optional.of(group(1L, null, 1, "FOOD")));
        when(groups.save(any(ItemGroup.class))).thenAnswer(inv -> {
            ItemGroup g = inv.getArgument(0);
            ReflectionTestUtils.setField(g, "uid", UidGenerator.next());
            return g;
        });

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
        when(groups.findByUid(existing.getUid())).thenReturn(Optional.of(existing));

        ItemGroupDto result = service.renameGroupByUid(existing.getUid(), new UpdateItemGroupRequestDto("Groceries"));

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
        when(groups.findByUid(dairy.getUid())).thenReturn(Optional.of(dairy));
        when(groups.findById(4L)).thenReturn(Optional.of(beverages));
        when(groups.findByCompanyId(COMPANY_ID)).thenReturn(List.of(food, dairy, milk, beverages));

        // move dairy under beverages: dairy L2->L2 (beverages.L1+1), milk follows
        service.moveGroupByUid(dairy.getUid(), new MoveItemGroupRequestDto(4L));

        assertThat(dairy.getParentId()).isEqualTo(4L);
        assertThat(dairy.getLevel()).isEqualTo(2);
        assertThat(milk.getLevel()).isEqualTo(3);

        // move dairy to root: L2->L1 (delta -1), milk L3->L2
        when(groups.findByCompanyId(COMPANY_ID)).thenReturn(List.of(food, dairy, milk, beverages));
        service.moveGroupByUid(dairy.getUid(), new MoveItemGroupRequestDto(null));
        assertThat(dairy.getParentId()).isNull();
        assertThat(dairy.getLevel()).isEqualTo(1);
        assertThat(milk.getLevel()).isEqualTo(2);
    }

    @Test
    void moveGroup_rejectsMovingUnderOwnDescendant() {
        ItemGroup food = group(1L, null, 1, "FOOD");
        ItemGroup dairy = group(2L, 1L, 2, "DAIRY");
        when(groups.findByUid(food.getUid())).thenReturn(Optional.of(food));
        when(groups.findById(2L)).thenReturn(Optional.of(dairy));
        when(groups.findByCompanyId(COMPANY_ID)).thenReturn(List.of(food, dairy));

        assertThatThrownBy(() -> service.moveGroupByUid(food.getUid(), new MoveItemGroupRequestDto(2L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("itself or its descendant");
    }

    @Test
    void archiveGroup_setsArchivedStatus() {
        ItemGroup existing = group(1L, null, 1, "FOOD");
        when(groups.findByUid(existing.getUid())).thenReturn(Optional.of(existing));
        when(items.countByItemGroupIdAndStatus(1L, ItemStatus.ACTIVE)).thenReturn(0L);

        service.archiveGroupByUid(existing.getUid());

        assertThat(existing.getStatus().name()).isEqualTo("ARCHIVED");
    }

    // -----------------------------------------------------------------------
    // ISSUE-CAT-002: archive must be blocked when active items reference the group
    // -----------------------------------------------------------------------

    @Test
    void archiveGroup_blockedWhenActiveItemsExist() {
        ItemGroup existing = group(1L, null, 1, "FOOD");
        when(groups.findByUid(existing.getUid())).thenReturn(Optional.of(existing));
        when(items.countByItemGroupIdAndStatus(1L, ItemStatus.ACTIVE)).thenReturn(3L);

        assertThatThrownBy(() -> service.archiveGroupByUid(existing.getUid()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("active item");

        // Status must not have changed
        assertThat(existing.getStatus()).isEqualTo(ItemStatus.ACTIVE);
        verify(items).countByItemGroupIdAndStatus(1L, ItemStatus.ACTIVE);
    }

    // -----------------------------------------------------------------------
    // ISSUE-CAT-003: depth limit of 4 levels enforced on create and move
    // -----------------------------------------------------------------------

    @Test
    void createGroup_rejectsDepthBeyondMaxLevel() {
        // Level-4 parent → child would be level 5 → rejected
        ItemGroup level4Parent = group(10L, 9L, ItemGroupServiceImpl.MAX_LEVEL, "L4");
        when(groups.existsByCompanyIdAndCode(COMPANY_ID, "L5")).thenReturn(false);
        when(groups.findById(10L)).thenReturn(Optional.of(level4Parent));

        assertThatThrownBy(() -> service.createGroup(new CreateItemGroupRequestDto(10L, "L5", "Level 5")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceed");
        verify(groups, never()).save(any());
    }

    @Test
    void createGroup_allowsExactlyMaxLevel() {
        // Level-3 parent → child is level 4 → allowed
        ItemGroup level3Parent = group(9L, 8L, ItemGroupServiceImpl.MAX_LEVEL - 1, "L3");
        when(groups.existsByCompanyIdAndCode(COMPANY_ID, "L4")).thenReturn(false);
        when(groups.findById(9L)).thenReturn(Optional.of(level3Parent));
        when(groups.save(any(ItemGroup.class))).thenAnswer(inv -> {
            ItemGroup g = inv.getArgument(0);
            g.setId(100L);
            ReflectionTestUtils.setField(g, "uid", UidGenerator.next());
            return g;
        });

        ItemGroupDto result = service.createGroup(new CreateItemGroupRequestDto(9L, "L4", "Level 4"));

        assertThat(result.level()).isEqualTo(ItemGroupServiceImpl.MAX_LEVEL);
    }

    @Test
    void moveGroup_rejectsWhenSubtreeWouldExceedMaxLevel() {
        // tree: root(L1) -> mid(L2) -> leaf(L3); target parent is at L3
        // moving mid under the L3 target → mid becomes L4, leaf becomes L5 → rejected
        ItemGroup root = group(1L, null, 1, "ROOT");
        ItemGroup mid = group(2L, 1L, 2, "MID");
        ItemGroup leaf = group(3L, 2L, 3, "LEAF");
        ItemGroup targetParent = group(4L, 1L, 3, "TARGET"); // L3 — moving mid under it: mid→L4, leaf→L5

        when(groups.findByUid(mid.getUid())).thenReturn(Optional.of(mid));
        when(groups.findById(4L)).thenReturn(Optional.of(targetParent));
        // first call for depth check, second call for subtree collection
        when(groups.findByCompanyId(COMPANY_ID)).thenReturn(List.of(root, mid, leaf, targetParent));

        assertThatThrownBy(() -> service.moveGroupByUid(mid.getUid(), new MoveItemGroupRequestDto(4L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceed");
    }

    @Test
    void requireGroup_notFound_throwsNoSuchElement() {
        String missingUid = UidGenerator.next();
        when(groups.findByUid(missingUid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renameGroupByUid(missingUid, new UpdateItemGroupRequestDto("x")))
            .isInstanceOf(NoSuchElementException.class);
    }
}

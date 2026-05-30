package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemGroupDto;
import com.orbix.engine.modules.catalog.domain.dto.MoveItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.entity.ItemGroup;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.repository.ItemGroupRepository;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemGroupServiceImpl implements ItemGroupService {

    /** PRD §3.1: item-group hierarchy must not exceed 4 levels. */
    static final int MAX_LEVEL = 4;

    private final ItemGroupRepository groups;
    private final ItemRepository items;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<ItemGroupDto> listGroups() {
        return groups.findByCompanyId(context.companyId()).stream()
            .sorted(Comparator.comparingInt(ItemGroup::getLevel).thenComparing(ItemGroup::getCode))
            .map(ItemGroupDto::from)
            .toList();
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "ItemGroup")
    public ItemGroupDto createGroup(CreateItemGroupRequestDto request) {
        Long companyId = context.companyId();
        String code = request.code().trim().toUpperCase();
        if (groups.existsByCompanyIdAndCode(companyId, code)) {
            throw new IllegalArgumentException("Item group code already exists: " + code);
        }
        int level = 1;
        if (request.parentId() != null) {
            level = requireGroupById(request.parentId()).getLevel() + 1;
        }
        if (level > MAX_LEVEL) {
            throw new IllegalArgumentException(
                "Item group hierarchy cannot exceed " + MAX_LEVEL + " levels");
        }
        ItemGroup group = groups.save(new ItemGroup(
            companyId, request.parentId(), level, code, request.name(), context.userId()));
        return ItemGroupDto.from(group);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "ItemGroup")
    public ItemGroupDto renameGroupByUid(String uid, UpdateItemGroupRequestDto request) {
        ItemGroup group = requireGroupByUid(uid);
        group.rename(request.name(), context.userId());
        return ItemGroupDto.from(group);
    }

    @Override
    @Transactional
    @Auditable(action = "MOVE", entityType = "ItemGroup")
    public ItemGroupDto moveGroupByUid(String uid, MoveItemGroupRequestDto request) {
        ItemGroup group = requireGroupByUid(uid);
        Long groupId = group.getId();
        Long newParentId = request.newParentId();

        int newLevel = 1;
        if (newParentId != null) {
            ItemGroup newParent = requireGroupById(newParentId);
            newLevel = newParent.getLevel() + 1;
        }
        // The subtree shifts by delta; the deepest leaf must still fit within MAX_LEVEL.
        int currentDepth = collectMaxDepth(groupId, groups.findByCompanyId(context.companyId()));
        int projectedMax = newLevel + (currentDepth - group.getLevel());
        if (projectedMax > MAX_LEVEL) {
            throw new IllegalArgumentException(
                "Move would push the subtree to level " + projectedMax
                    + " which exceeds the maximum of " + MAX_LEVEL);
        }

        List<ItemGroup> companyGroups = groups.findByCompanyId(context.companyId());
        Set<Long> subtree = collectSubtree(groupId, companyGroups);
        if (newParentId != null && subtree.contains(newParentId)) {
            throw new IllegalArgumentException("Cannot move a group under itself or its descendant");
        }

        Long actorId = context.userId();
        int delta = newLevel - group.getLevel();
        group.moveTo(newParentId, newLevel, actorId);
        if (delta != 0) {
            companyGroups.stream()
                .filter(g -> !g.getId().equals(groupId) && subtree.contains(g.getId()))
                .forEach(g -> g.shiftLevel(delta, actorId));
        }
        return ItemGroupDto.from(group);
    }

    @Override
    @Transactional
    @Auditable(action = "ARCHIVE", entityType = "ItemGroup")
    public void archiveGroupByUid(String uid) {
        ItemGroup group = requireGroupByUid(uid);
        long activeItemCount = items.countByItemGroupIdAndStatus(group.getId(), ItemStatus.ACTIVE);
        if (activeItemCount > 0) {
            throw new IllegalArgumentException(
                "Cannot archive group '" + group.getCode() + "': it still has "
                    + activeItemCount + " active item(s). Archive or move items first.");
        }
        group.archive(context.userId());
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVATE", entityType = "ItemGroup")
    public void activateGroupByUid(String uid) {
        ItemGroup group = requireGroupByUid(uid);
        if (group.getStatus() == ItemStatus.ACTIVE) {
            throw new IllegalArgumentException("Item group is already active: " + uid);
        }
        group.activate(context.userId());
    }

    /**
     * The maximum level (depth) of any node in the subtree rooted at {@code rootId},
     * computed from an already-loaded company group list to avoid extra queries.
     */
    private int collectMaxDepth(Long rootId, List<ItemGroup> companyGroups) {
        Map<Long, List<ItemGroup>> childrenByParent = companyGroups.stream()
            .filter(g -> g.getParentId() != null)
            .collect(Collectors.groupingBy(ItemGroup::getParentId));
        Map<Long, Integer> levelById = companyGroups.stream()
            .collect(Collectors.toMap(ItemGroup::getId, ItemGroup::getLevel));

        int max = levelById.getOrDefault(rootId, 1);
        Deque<Long> queue = new ArrayDeque<>(List.of(rootId));
        Set<Long> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!visited.add(current)) continue;
            for (ItemGroup child : childrenByParent.getOrDefault(current, new ArrayList<>())) {
                max = Math.max(max, levelById.getOrDefault(child.getId(), child.getLevel()));
                queue.add(child.getId());
            }
        }
        return max;
    }

    /** The group plus all of its descendants, by id. */
    private Set<Long> collectSubtree(Long rootId, List<ItemGroup> companyGroups) {
        Map<Long, List<ItemGroup>> childrenByParent = companyGroups.stream()
            .filter(g -> g.getParentId() != null)
            .collect(Collectors.groupingBy(ItemGroup::getParentId));
        Set<Long> subtree = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>(List.of(rootId));
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!subtree.add(current)) {
                continue;
            }
            for (ItemGroup child : childrenByParent.getOrDefault(current, new ArrayList<>())) {
                queue.add(child.getId());
            }
        }
        return subtree;
    }

    private ItemGroup requireGroupById(Long groupId) {
        ItemGroup group = groups.findById(groupId)
            .orElseThrow(() -> new NoSuchElementException("Item group not found: " + groupId));
        if (!Objects.equals(group.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Item group not found: " + groupId);
        }
        return group;
    }

    private ItemGroup requireGroupByUid(String uid) {
        ItemGroup group = groups.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Item group not found: " + uid));
        if (!Objects.equals(group.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Item group not found: " + uid);
        }
        return group;
    }
}

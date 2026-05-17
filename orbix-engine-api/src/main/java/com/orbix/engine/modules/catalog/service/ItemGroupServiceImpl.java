package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemGroupDto;
import com.orbix.engine.modules.catalog.domain.dto.MoveItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateItemGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.entity.ItemGroup;
import com.orbix.engine.modules.catalog.repository.ItemGroupRepository;
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

    private final ItemGroupRepository groups;
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
        requireGroupByUid(uid).archive(context.userId());
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

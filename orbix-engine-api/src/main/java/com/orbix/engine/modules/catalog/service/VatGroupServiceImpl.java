package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateVatGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateVatGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.VatGroupDto;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VatGroupServiceImpl implements VatGroupService {

    private final VatGroupRepository vatGroups;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<VatGroupDto> listVatGroups() {
        return vatGroups.findByCompanyId(context.companyId()).stream()
            .sorted(Comparator.comparing(VatGroup::getCode))
            .map(VatGroupDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VatGroupDto getVatGroupByUid(String uid) {
        return VatGroupDto.from(requireVatGroupByUid(uid));
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "VatGroup")
    public VatGroupDto createVatGroup(CreateVatGroupRequestDto request) {
        Long companyId = context.companyId();
        String code = request.code().trim().toUpperCase();
        if (vatGroups.existsByCompanyIdAndCode(companyId, code)) {
            throw new IllegalArgumentException("VAT group code already exists: " + code);
        }
        VatGroup group = vatGroups.save(new VatGroup(companyId, code, request.name(),
            request.rate(), request.validFrom(), request.isDefault(), context.userId()));
        if (request.isDefault()) {
            clearOtherDefaults(companyId, group.getId());
        }
        return VatGroupDto.from(group);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "VatGroup")
    public VatGroupDto updateVatGroupByUid(String uid, UpdateVatGroupRequestDto request) {
        VatGroup group = requireVatGroupByUid(uid);
        Long actorId = context.userId();
        group.update(request.name(), request.rate(), request.validFrom(), actorId);
        group.setAsDefault(request.isDefault(), actorId);
        if (request.isDefault()) {
            clearOtherDefaults(group.getCompanyId(), group.getId());
        }
        return VatGroupDto.from(group);
    }

    @Override
    @Transactional
    @Auditable(action = "ARCHIVE", entityType = "VatGroup")
    public void archiveVatGroupByUid(String uid) {
        VatGroup group = requireVatGroupByUid(uid);
        if (group.getStatus() == ItemStatus.ARCHIVED) {
            throw new IllegalArgumentException("VAT group is already archived: " + uid);
        }
        group.archive(context.userId());
    }

    private void clearOtherDefaults(Long companyId, Long keepId) {
        Long actorId = context.userId();
        vatGroups.findByCompanyIdAndIsDefaultTrue(companyId).stream()
            .filter(g -> !g.getId().equals(keepId))
            .forEach(g -> g.setAsDefault(false, actorId));
    }

    private VatGroup requireVatGroupByUid(String uid) {
        VatGroup group = vatGroups.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("VAT group not found: " + uid));
        if (!Objects.equals(group.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("VAT group not found: " + uid);
        }
        return group;
    }
}

package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateUomRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UomDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateUomRequestDto;
import com.orbix.engine.modules.catalog.domain.entity.Uom;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.UomDimension;
import com.orbix.engine.modules.catalog.repository.UomRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class UomServiceImpl implements UomService {

    private final UomRepository uoms;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<UomDto> listUoms() {
        return uoms.findAll().stream()
            .sorted(Comparator.comparing(Uom::getCode))
            .map(UomDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UomDto getUomByUid(String uid) {
        return UomDto.from(requireUomByUid(uid));
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "Uom")
    public UomDto createUom(CreateUomRequestDto request) {
        String code = request.code().trim().toUpperCase();
        if (uoms.existsByCode(code)) {
            throw new IllegalArgumentException("UoM code already exists: " + code);
        }
        Long actorId = context.userId();
        Uom uom = uoms.save(new Uom(code, request.name(), request.dimension(), request.base(), actorId));
        if (uom.isBase()) {
            clearOtherBases(uom.getDimension(), uom.getId(), actorId);
        }
        return UomDto.from(uom);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Uom")
    public UomDto updateUomByUid(String uid, UpdateUomRequestDto request) {
        Uom uom = requireUomByUid(uid);
        Long actorId = context.userId();
        uom.update(request.name(), request.dimension(), request.base(), actorId);
        if (uom.isBase()) {
            clearOtherBases(uom.getDimension(), uom.getId(), actorId);
        }
        return UomDto.from(uom);
    }

    @Override
    @Transactional
    @Auditable(action = "ARCHIVE", entityType = "Uom")
    public void archiveUomByUid(String uid) {
        Uom uom = requireUomByUid(uid);
        if (uom.getStatus() == ItemStatus.ARCHIVED) {
            throw new IllegalArgumentException("UoM is already archived: " + uid);
        }
        uom.archive(context.userId());
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVATE", entityType = "Uom")
    public void activateUomByUid(String uid) {
        Uom uom = requireUomByUid(uid);
        if (uom.getStatus() == ItemStatus.ACTIVE) {
            throw new IllegalArgumentException("UoM is already active: " + uid);
        }
        uom.activate(context.userId());
    }

    /** Keep at most one base unit per dimension: demote any other current base. */
    private void clearOtherBases(UomDimension dimension, Long keepId, Long actorId) {
        uoms.findByDimensionAndBaseTrue(dimension).stream()
            .filter(u -> !u.getId().equals(keepId))
            .forEach(u -> u.clearBase(actorId));
    }

    private Uom requireUomByUid(String uid) {
        return uoms.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("UoM not found: " + uid));
    }
}

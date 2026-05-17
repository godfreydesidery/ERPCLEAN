package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateUomRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UomDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateUomRequestDto;
import com.orbix.engine.modules.catalog.domain.entity.Uom;
import com.orbix.engine.modules.catalog.repository.UomRepository;
import com.orbix.engine.modules.common.service.Auditable;
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
        return UomDto.from(uoms.save(new Uom(code, request.name(), request.dimension(), request.base())));
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Uom")
    public UomDto updateUomByUid(String uid, UpdateUomRequestDto request) {
        Uom uom = requireUomByUid(uid);
        uom.update(request.name(), request.dimension(), request.base());
        return UomDto.from(uom);
    }

    private Uom requireUomByUid(String uid) {
        return uoms.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("UoM not found: " + uid));
    }
}

package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.pos.domain.dto.CreateTillRequestDto;
import com.orbix.engine.modules.pos.domain.dto.TillDto;
import com.orbix.engine.modules.pos.domain.dto.UpdateTillRequestDto;
import com.orbix.engine.modules.pos.domain.entity.Till;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import com.orbix.engine.modules.pos.repository.TillRepository;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TillServiceImpl implements TillService {

    private static final String AGG = "Till";
    private static final String F_ID = "tillId";
    private static final String F_CODE = "code";

    private final TillRepository tills;
    private final TillSessionRepository tillSessions;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = AGG)
    public TillDto create(CreateTillRequestDto request) {
        branchScope.requireAccess(request.branchId());
        Long companyId = context.companyId();
        Long actorId = context.userId();
        String code = request.code().trim().toUpperCase();
        if (tills.existsByBranchIdAndCode(request.branchId(), code)) {
            throw new IllegalArgumentException(
                "Till code already exists for this branch: " + code);
        }
        Till till = tills.save(new Till(companyId, request.branchId(), code, request.name(),
            request.defaultPriceListId(), actorId));
        if (request.installId() != null && !request.installId().isBlank()) {
            till.update(till.getName(), till.getDefaultPriceListId(), request.installId().trim(), actorId);
        }
        events.publish("TillCreated.v1", AGG, String.valueOf(till.getId()),
            Map.of(F_ID, till.getId(), F_CODE, till.getCode(),
                "branchId", till.getBranchId(), "name", till.getName()));
        return TillDto.from(till);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = AGG)
    public TillDto update(String uid, UpdateTillRequestDto request) {
        Till till = requireTillByUid(uid);
        till.update(request.name(), request.defaultPriceListId(),
            request.installId() != null ? request.installId().trim() : null, context.userId());
        return TillDto.from(till);
    }

    @Override
    @Transactional
    @Auditable(action = "DEACTIVATE", entityType = AGG)
    public TillDto deactivate(String uid) {
        Till till = requireTillByUid(uid);
        Optional<TillSession> open = tillSessions.findFirstByTillIdAndStatus(
            till.getId(), TillSessionStatus.OPEN);
        if (open.isPresent()) {
            throw new IllegalArgumentException(
                "Cannot deactivate a till with an OPEN session (session " + open.get().getId() + ")");
        }
        till.deactivate(context.userId());
        events.publish("TillDeactivated.v1", AGG, String.valueOf(till.getId()),
            Map.of(F_ID, till.getId(), F_CODE, till.getCode()));
        return TillDto.from(till);
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVATE", entityType = AGG)
    public TillDto activate(String uid) {
        Till till = requireTillByUid(uid);
        till.activate(context.userId());
        events.publish("TillActivated.v1", AGG, String.valueOf(till.getId()),
            Map.of(F_ID, till.getId(), F_CODE, till.getCode()));
        return TillDto.from(till);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TillDto> list(Long branchId) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        List<Till> rows = scope == null
            ? tills.findByCompanyIdOrderByIdAsc(companyId)
            : tills.findByCompanyIdAndBranchIdOrderByIdAsc(companyId, scope);
        return rows.stream().map(TillDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TillDto get(String uid) {
        return TillDto.from(requireTillByUid(uid));
    }

    private Till requireTillByUid(String uid) {
        Till till = tills.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Till not found: " + uid));
        if (!Objects.equals(till.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Till not found: " + uid);
        }
        branchScope.requireAccess(till.getBranchId());
        return till;
    }
}

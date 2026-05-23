package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.common.domain.dto.AuditIntegrityResultDto;
import com.orbix.engine.modules.common.domain.dto.AuditLogDto;
import com.orbix.engine.modules.common.domain.dto.AuditPageDto;
import com.orbix.engine.modules.common.domain.entity.AuditLog;
import com.orbix.engine.modules.common.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditQueryServiceImpl implements AuditQueryService {

    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogRepository repo;

    @Override
    @Transactional(readOnly = true)
    public AuditPageDto search(Long actorId, String action, String entityType, String entityId,
                               Long branchId, Instant from, Instant to, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Page<AuditLog> result = repo.search(actorId, blankToNull(action), blankToNull(entityType),
            blankToNull(entityId), branchId, from, to, PageRequest.of(safePage, safeSize));
        return new AuditPageDto(
            result.getContent().stream().map(AuditLogDto::from).toList(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public AuditIntegrityResultDto verify(Instant from, Instant to) {
        Instant lo = from != null ? from : Instant.EPOCH;
        Instant hi = to != null ? to : Instant.now();
        List<AuditLog> rows = repo.findByAtBetweenOrderByIdAsc(lo, hi);

        long verified = 0;
        AuditLog prev = null;
        for (AuditLog r : rows) {
            String recomputed = AuditHash.rowHash(r, r.getPrevHash());
            if (!recomputed.equals(r.getRowHash())) {
                return new AuditIntegrityResultDto(false, verified, r.getId(),
                    "Row " + r.getId() + " content does not match its stored hash");
            }
            if (prev != null && !r.getPrevHash().equals(prev.getRowHash())) {
                return new AuditIntegrityResultDto(false, verified, r.getId(),
                    "Chain link broken at row " + r.getId() + " (a prior row was altered or removed)");
            }
            prev = r;
            verified++;
        }
        return new AuditIntegrityResultDto(true, verified, null,
            "OK · " + verified + " rows verified");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}

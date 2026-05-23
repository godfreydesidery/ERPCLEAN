package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.common.domain.entity.AuditLog;
import com.orbix.engine.modules.common.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogWriterImpl implements AuditLogWriter {

    private final AuditLogRepository repo;

    @Override
    public void write(Record entry) {
        try {
            String prevHash = repo.findTopByOrderByIdDesc()
                .map(AuditLog::getRowHash)
                .orElse(AuditHash.GENESIS);

            AuditLog row = new AuditLog();
            // Whole-second precision so the stored value re-reads identically and
            // the hash stays verifiable (the column is second-resolution).
            row.setAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
            row.setActorId(entry.actorId() == null ? 0L : entry.actorId());
            row.setAction(entry.action());
            row.setEntityType(entry.entityType());
            row.setEntityId(blankToDash(entry.entityId()));
            row.setCompanyId(entry.companyId());
            row.setBranchId(entry.branchId());
            row.setAfterJson(entry.afterJson());
            row.setMetaJson(entry.metaJson());
            row.setPrevHash(prevHash);
            row.setRowHash(AuditHash.rowHash(row, prevHash));
            repo.save(row);
        } catch (Exception ex) {
            // Audit failure must never roll back or mask the business operation.
            log.error("Failed to write audit row action={} entity={}",
                entry.action(), entry.entityType(), ex);
        }
    }

    private static String blankToDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}

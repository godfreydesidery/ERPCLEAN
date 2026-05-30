package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.common.domain.entity.AuditLog;
import com.orbix.engine.modules.common.repository.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Hash-chained audit writer. The head-read + insert pair is serialized with a
 * JVM-level lock so concurrent audited operations cannot fork the chain by both
 * reading the same predecessor hash (ISSUE-AUDIT-01). The lock is held only for
 * the duration of the DB round-trip, so contention is minimal under normal load.
 *
 * The method runs in its own REQUIRES_NEW transaction so the audit row commits
 * independently of the calling business transaction — failure never rolls back
 * the business write, and the hash chain reflects committed order.
 */
@Service
@Slf4j
public class AuditLogWriterImpl implements AuditLogWriter {

    private final AuditLogRepository repo;
    /** Single lock serialises the read-head → compute-hash → insert sequence. */
    private final ReentrantLock chainLock = new ReentrantLock();

    public AuditLogWriterImpl(AuditLogRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(Record entry) {
        chainLock.lock();
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
            row.setBeforeJson(entry.beforeJson());
            row.setAfterJson(entry.afterJson());
            row.setMetaJson(entry.metaJson());
            row.setPrevHash(prevHash);
            row.setRowHash(AuditHash.rowHash(row, prevHash));
            repo.save(row);
        } catch (Exception ex) {
            // Audit failure must never roll back or mask the business operation.
            log.error("Failed to write audit row action={} entity={}",
                entry.action(), entry.entityType(), ex);
        } finally {
            chainLock.unlock();
        }
    }

    private static String blankToDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}

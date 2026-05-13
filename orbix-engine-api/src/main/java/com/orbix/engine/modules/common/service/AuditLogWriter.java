package com.orbix.engine.modules.common.service;

import org.springframework.stereotype.Component;

/**
 * Persists audit rows. The real implementation writes to {@code audit_log}
 * with hash-chained integrity (DATA-MODEL.md §1.9).
 * Scaffold leaves the body to be filled in during MVP work.
 */
@Component
public class AuditLogWriter {

    public void write(Record record) {
        // TODO: persist to audit_log with prev_hash / row_hash chain.
    }

    public record Record(
        Long actorId,
        Long companyId,
        Long branchId,
        String action,
        String entityType,
        String afterJson
    ) {}
}

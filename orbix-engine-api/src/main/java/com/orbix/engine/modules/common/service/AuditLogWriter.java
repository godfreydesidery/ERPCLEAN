package com.orbix.engine.modules.common.service;

/**
 * Persists audit rows. The real implementation writes to {@code audit_log}
 * with hash-chained integrity (DATA-MODEL.md §1.9).
 */
public interface AuditLogWriter {

    void write(Record entry);

    record Record(
        Long actorId,
        Long companyId,
        Long branchId,
        String action,
        String entityType,
        String afterJson
    ) {}
}

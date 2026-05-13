package com.orbix.engine.modules.common.service;

import org.springframework.stereotype.Service;

@Service
public class AuditLogWriterImpl implements AuditLogWriter {

    @Override
    public void write(Record entry) {
        // TODO: persist to audit_log with prev_hash / row_hash chain.
    }
}

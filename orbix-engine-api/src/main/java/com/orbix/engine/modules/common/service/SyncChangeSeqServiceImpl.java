package com.orbix.engine.modules.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * DB-agnostic implementation of {@link SyncChangeSeqService}.
 *
 * <p>Fetches the next value from the {@code sync_change_seq} sequence using
 * the same dialect-fallback pattern as {@code DevSeed.nextVal}:
 * <ol>
 *   <li>Try MariaDB / SQL-standard syntax: {@code SELECT NEXT VALUE FOR sync_change_seq}</li>
 *   <li>Fall back to PostgreSQL syntax: {@code SELECT nextval('sync_change_seq')}</li>
 * </ol>
 *
 * <p>This is intentionally a raw JDBC call, not a JPA entity or
 * {@code @NativeQuery}. Mapping a dummy entity purely to read a sequence
 * value costs more than it saves, and JDBC is exactly the tool for
 * DDL-adjacent calls. The two-dialect try/catch is the established pattern
 * in this codebase (see DevSeed.java) and is the minimal portable approach.
 * If a dialect-resolver abstraction is promoted to a shared adapter later,
 * this class is the one place to update.
 */
@Service
@RequiredArgsConstructor
public class SyncChangeSeqServiceImpl implements SyncChangeSeqService {

    private static final String SEQ_NAME = "sync_change_seq";

    private final JdbcTemplate jdbc;

    @Override
    public long next() {
        // DB-agnostic next-value lookup.
        // MariaDB / SQL-standard: NEXT VALUE FOR seq_name (identifier, no quotes).
        // PostgreSQL: nextval('seq_name') (function call with a string literal).
        try {
            Long val = jdbc.queryForObject(
                "SELECT NEXT VALUE FOR " + SEQ_NAME, Long.class);
            if (val == null) {
                throw new IllegalStateException("sync_change_seq returned NULL");
            }
            return val;
        } catch (Exception mariaEx) {
            try {
                Long val = jdbc.queryForObject(
                    "SELECT nextval('" + SEQ_NAME + "')", Long.class);
                if (val == null) {
                    throw new IllegalStateException("sync_change_seq returned NULL");
                }
                return val;
            } catch (Exception pgEx) {
                // Rethrow with both failure messages to aid diagnosis
                IllegalStateException ex = new IllegalStateException(
                    "Failed to fetch next sync_change_seq value. "
                        + "MariaDB error: " + mariaEx.getMessage() + ". "
                        + "PostgreSQL error: " + pgEx.getMessage());
                ex.addSuppressed(mariaEx);
                ex.addSuppressed(pgEx);
                throw ex;
            }
        }
    }
}

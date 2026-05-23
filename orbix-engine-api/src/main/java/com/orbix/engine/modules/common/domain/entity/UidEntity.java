package com.orbix.engine.modules.common.domain.entity;

import com.orbix.engine.modules.common.util.UidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Base class for entities whose UID is exposed externally — in URLs, API
 * response DTOs, and cross-system references. Subclasses keep their own
 * numeric {@code @Id} (one sequence per table) which stays internal: it
 * never leaves the boundary of the modular monolith.
 *
 * <p>Why ULID and not UUID:
 * <ul>
 *   <li>26 chars vs 36 — fits in {@code CHAR(26)} with no dashes.</li>
 *   <li>Lexicographically sortable by creation time — friendlier on paging,
 *       cache locality, and human eyeballing of "which came first."</li>
 *   <li>Crockford base32 — no {@code I}/{@code L}/{@code O}/{@code U} so
 *       transcription is robust.</li>
 * </ul>
 *
 * <p>Subclasses MUST add this column to their {@code CREATE TABLE} migration:
 * <pre>uid CHAR(26) NOT NULL,
 *CONSTRAINT uk_&lt;table&gt;_uid UNIQUE (uid)</pre>
 *
 * <p>The {@link #assignUid()} hook fires at {@code @PrePersist} time, so
 * application code never sets the uid — it's filled in just before the
 * insert. Tests that pre-set the field for fixtures use {@link #setUid}.
 */
@MappedSuperclass
@Getter
public abstract class UidEntity {

    @Setter(AccessLevel.PROTECTED) // tests + Flyway-Java migrations only; never app code
    // CHAR(26), not VARCHAR: ULIDs are always exactly 26 chars (see migrations
    // and CLAUDE.md). @JdbcTypeCode pins the SQL type so ddl-auto=validate
    // matches the CHAR(26) columns; portable across MariaDB and Postgres.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uid", nullable = false, length = 26, unique = true, updatable = false)
    private String uid;

    @PrePersist
    void assignUid() {
        if (uid == null) {
            uid = UidGenerator.next();
        }
    }
}

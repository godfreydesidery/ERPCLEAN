package com.orbix.engine.modules.common.repository;

import com.orbix.engine.modules.common.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** The most recent row — its hash is the {@code prev_hash} for the next write. */
    Optional<AuditLog> findTopByOrderByIdDesc();

    /** Rows in a time range, oldest first — the order the chain was written in. */
    List<AuditLog> findByAtBetweenOrderByIdAsc(Instant from, Instant to);

    /**
     * Filtered, paginated read for the audit viewer (US-IAM-013). Every filter
     * is optional: a null argument disables that predicate.
     */
    @Query("""
        select a from AuditLog a
        where (:actorId is null or a.actorId = :actorId)
          and (:action is null or a.action = :action)
          and (:entityType is null or a.entityType = :entityType)
          and (:entityId is null or a.entityId = :entityId)
          and (:branchId is null or a.branchId = :branchId)
          and (:from is null or a.at >= :from)
          and (:to is null or a.at <= :to)
        order by a.id desc
        """)
    Page<AuditLog> search(@Param("actorId") Long actorId,
                          @Param("action") String action,
                          @Param("entityType") String entityType,
                          @Param("entityId") String entityId,
                          @Param("branchId") Long branchId,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}

package com.orbix.engine.platform.events;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Transactional outbox row. Inserted in the same DB transaction as the
 * business write that produced it. A poller dispatches PENDING rows.
 * See ARCHITECTURE.md §2.10 and DATA-MODEL.md §1.11.
 */
@Entity
@Table(name = "domain_event")
public class DomainEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "domain_event_seq")
    @SequenceGenerator(name = "domain_event_seq", sequenceName = "domain_event_seq", allocationSize = 50)
    private Long id;

    /** Versioned, e.g. "SalesInvoicePosted.v1" */
    @Column(nullable = false, length = 120)
    private String type;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 80)
    private String aggregateId;

    @Lob
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "actor_id")
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    public enum Status { PENDING, DISPATCHED, FAILED, DEAD_LETTERED }

    protected DomainEvent() {}

    public DomainEvent(String type, String aggregateType, String aggregateId,
                       String payloadJson, Long companyId, Long branchId, Long actorId) {
        this.type = type;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payloadJson = payloadJson;
        this.occurredAt = Instant.now();
        this.companyId = companyId;
        this.branchId = branchId;
        this.actorId = actorId;
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getPayloadJson() { return payloadJson; }
    public Instant getOccurredAt() { return occurredAt; }
    public Long getCompanyId() { return companyId; }
    public Long getBranchId() { return branchId; }
    public Long getActorId() { return actorId; }
    public Status getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }

    public void markDispatched() {
        this.status = Status.DISPATCHED;
        this.dispatchedAt = Instant.now();
    }

    public void recordFailure(String error) {
        this.attemptCount++;
        this.lastAttemptAt = Instant.now();
        this.lastError = error;
    }

    public void markDeadLettered() {
        this.status = Status.DEAD_LETTERED;
    }
}

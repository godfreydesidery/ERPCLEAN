package com.orbix.engine.modules.party.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.party.domain.enums.PartyNoteKind;
import com.orbix.engine.modules.party.domain.enums.PartyNoteStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Slice G — chase / activity note attached to a {@link Party}. Generalised
 * across the AR + AP sides so the supplier-AP slice (G.1) reuses this
 * table. Notes are append-only; correction is via {@link #archive(Long)}
 * and a fresh row.
 */
@Entity
@Table(
    name = "party_note",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_party_note_uid", columnNames = {"uid"})
    }
)
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class PartyNote extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "party_note_seq")
    @SequenceGenerator(name = "party_note_seq", sequenceName = "party_note_seq", allocationSize = 50)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartyNoteKind kind;

    @Column(nullable = false, length = 1000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartyNoteStatus status = PartyNoteStatus.ACTIVE;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by")
    private Long archivedBy;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "updated_by", nullable = false) private Long updatedBy;

    public PartyNote(Long companyId, Long partyId, PartyNoteKind kind, String body, Long actorId) {
        this.companyId = companyId;
        this.partyId = partyId;
        this.kind = kind;
        this.body = body;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public void archive(Long actorId) {
        this.status = PartyNoteStatus.ARCHIVED;
        Instant now = Instant.now();
        this.archivedAt = now;
        this.archivedBy = actorId;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }
}

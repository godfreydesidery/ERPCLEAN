package com.orbix.engine.modules.party.repository;

import com.orbix.engine.modules.party.domain.entity.PartyNote;
import com.orbix.engine.modules.party.domain.enums.PartyNoteKind;
import com.orbix.engine.modules.party.domain.enums.PartyNoteStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartyNoteRepository extends JpaRepository<PartyNote, Long> {

    Optional<PartyNote> findByUid(String uid);

    /** Activity log for a single customer drill-down — newest first. */
    List<PartyNote> findByPartyIdAndStatusOrderByCreatedAtDescIdDesc(
        Long partyId, PartyNoteStatus status, Pageable pageable);

    /** Variant including any status (so the FE can filter ARCHIVED in). */
    List<PartyNote> findByPartyIdOrderByCreatedAtDescIdDesc(
        Long partyId, Pageable pageable);

    /** Future chase-activity roll-up: scoped by company + kind + lifecycle. */
    List<PartyNote> findByCompanyIdAndKindAndStatusOrderByCreatedAtDescIdDesc(
        Long companyId, PartyNoteKind kind, PartyNoteStatus status, Pageable pageable);
}

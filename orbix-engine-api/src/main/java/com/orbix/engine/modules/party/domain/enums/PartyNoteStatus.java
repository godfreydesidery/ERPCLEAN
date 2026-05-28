package com.orbix.engine.modules.party.domain.enums;

/** Lifecycle of a {@code party_note}. Notes are append-only — edit by archive + repost. */
public enum PartyNoteStatus {
    ACTIVE,
    ARCHIVED
}

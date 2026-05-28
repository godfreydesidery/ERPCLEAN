package com.orbix.engine.modules.party.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.party.domain.enums.PartyNoteKind;
import com.orbix.engine.modules.party.domain.enums.PartyNoteStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PartyNoteDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void activeNote_serialisesIdsAsStrings() throws Exception {
        PartyNoteDto dto = new PartyNoteDto(
            123L, "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            456L, PartyNoteKind.AR_CHASE, "Called - left voicemail",
            PartyNoteStatus.ACTIVE,
            Instant.parse("2026-05-28T09:00:00Z"), 7L,
            null, null
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"123\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"partyId\":\"456\"");
        assertThat(json).contains("\"kind\":\"AR_CHASE\"");
        assertThat(json).contains("\"body\":\"Called - left voicemail\"");
        assertThat(json).contains("\"status\":\"ACTIVE\"");
        assertThat(json).contains("\"createdAt\":\"2026-05-28T09:00:00Z\"");
        assertThat(json).contains("\"createdBy\":\"7\"");
        assertThat(json).contains("\"archivedAt\":null");
        assertThat(json).contains("\"archivedBy\":null");
    }

    @Test
    void archivedNote_carriesArchiveStamp() throws Exception {
        PartyNoteDto dto = new PartyNoteDto(
            123L, "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            456L, PartyNoteKind.AR_CHASE, "Body",
            PartyNoteStatus.ARCHIVED,
            Instant.parse("2026-05-28T09:00:00Z"), 7L,
            Instant.parse("2026-05-28T11:00:00Z"), 9L
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"status\":\"ARCHIVED\"");
        assertThat(json).contains("\"archivedAt\":\"2026-05-28T11:00:00Z\"");
        assertThat(json).contains("\"archivedBy\":\"9\"");
    }
}

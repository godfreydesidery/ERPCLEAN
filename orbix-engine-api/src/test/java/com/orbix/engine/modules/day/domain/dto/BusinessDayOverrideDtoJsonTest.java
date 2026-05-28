package com.orbix.engine.modules.day.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link BusinessDayOverrideDto} so the JSON:API
 * string-id discipline doesn't silently regress. Surrogate-Long PK aggregate:
 * exposes both {@code id} (stringified by the global Long-id modifier) and
 * {@code uid} (external URL handle). Archive lifecycle columns serialise as
 * {@code archivedAt} (nullable Instant) and {@code archivedBy}
 * (nullable Long; stringified when present).
 */
class BusinessDayOverrideDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void uid_and_all_id_fields_serialise_as_strings_archive_columns_null_when_active() throws Exception {
        BusinessDayOverrideDto dto = new BusinessDayOverrideDto(
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            7L,
            42L,
            LocalDate.of(2026, 5, 27),
            "POS_SALE",
            123L,
            "Back-dated sales tally",
            9L,
            Instant.parse("2026-05-27T06:00:00Z"),
            null,
            null
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        // Surrogate id stringifies under the global modifier.
        assertThat(json).contains("\"id\":\"7\"");
        // Composite components on the override payload.
        assertThat(json).contains("\"branchId\":\"42\"");
        assertThat(json).contains("\"targetBusinessDate\":\"2026-05-27\"");
        assertThat(json).contains("\"entityType\":\"POS_SALE\"");
        assertThat(json).contains("\"entityId\":\"123\"");
        assertThat(json).contains("\"authorisedBy\":\"9\"");
        // Active record: archive columns are null.
        assertThat(json).contains("\"archivedAt\":null");
        assertThat(json).contains("\"archivedBy\":null");
    }

    @Test
    void archived_at_and_by_serialise_as_iso_and_string_when_voided() throws Exception {
        BusinessDayOverrideDto dto = new BusinessDayOverrideDto(
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            7L,
            42L,
            LocalDate.of(2026, 5, 27),
            "POS_SALE",
            123L,
            "Back-dated sales tally",
            9L,
            Instant.parse("2026-05-27T06:00:00Z"),
            Instant.parse("2026-05-27T07:30:00Z"),
            11L
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"archivedAt\":\"2026-05-27T07:30:00Z\"");
        assertThat(json).contains("\"archivedBy\":\"11\"");
    }
}

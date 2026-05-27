package com.orbix.engine.modules.day.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.day.domain.enums.BusinessDayStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link BusinessDayDto} so the JSON:API string-id
 * discipline and ADR 0002's composite-PK uid pattern don't silently regress.
 * The composite-PK aggregate exposes {@code uid} as the external handle plus
 * the composite components ({@code branchId} as a JSON string per the global
 * Long-id rule, {@code businessDate} as ISO-8601). No surrogate {@code id} —
 * the composite is the identity.
 */
class BusinessDayDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void uid_and_all_id_fields_serialise_as_strings_composite_components_pinned() throws Exception {
        BusinessDayDto dto = new BusinessDayDto(
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            42L,
            LocalDate.of(2026, 5, 27),
            BusinessDayStatus.OPEN,
            Instant.parse("2026-05-27T06:00:00Z"),
            7L,
            null,
            null,
            null
        );

        String json = mapper.writeValueAsString(dto);

        // External URL handle — uid is a string already.
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        // Composite components: branchId stringifies under the global modifier.
        assertThat(json).contains("\"branchId\":\"42\"");
        // businessDate is ISO-8601 LocalDate (not a numeric).
        assertThat(json).contains("\"businessDate\":\"2026-05-27\"");
        // Other Long fields stringify too.
        assertThat(json).contains("\"openedBy\":\"7\"");
        // Status renders as the enum's string name.
        assertThat(json).contains("\"status\":\"OPEN\"");
        // No surrogate id field on this aggregate.
        assertThat(json).doesNotContain("\"id\":");
    }
}

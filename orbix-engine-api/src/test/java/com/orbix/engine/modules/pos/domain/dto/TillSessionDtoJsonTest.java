package com.orbix.engine.modules.pos.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link TillSessionDto}: {@code uid} is the
 * external identifier and {@code id} / all {@code *Id} fields serialise as
 * JSON strings (JSON:API discipline, driven by
 * {@link IdLongAsStringSerializerModifier}). Genuine numerics stay numeric.
 */
class TillSessionDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_id_fields_serialise_as_strings_uid_stays_string() throws Exception {
        TillSessionDto dto = new TillSessionDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            100L,
            5L,
            2L,
            LocalDate.of(2026, 5, 13),
            4L,
            Instant.parse("2026-05-13T06:00:00Z"),
            new BigDecimal("50000.0000"),
            null,
            null,
            null,
            null,
            null,
            null,
            TillSessionStatus.OPEN,
            null
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"tillId\":\"100\"");
        assertThat(json).contains("\"branchId\":\"5\"");
        assertThat(json).contains("\"companyId\":\"2\"");
        assertThat(json).contains("\"openedBy\":\"4\"");
        // Genuine numerics untouched.
        assertThat(json).contains("\"openingFloatAmount\":50000.0000");
    }
}

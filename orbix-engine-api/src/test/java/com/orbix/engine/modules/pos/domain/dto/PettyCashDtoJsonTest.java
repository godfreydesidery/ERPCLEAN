package com.orbix.engine.modules.pos.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.pos.domain.enums.PettyCashCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link PettyCashDto}. Append-only — no archive
 * lifecycle columns; uid + id stringify, money stays numeric.
 */
class PettyCashDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void uid_and_id_fields_serialise_as_strings_amounts_stay_numeric() throws Exception {
        PettyCashDto dto = new PettyCashDto(
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            6500L,
            200L,
            42L,
            LocalDate.of(2026, 5, 27),
            new BigDecimal("2000.0000"),
            Instant.parse("2026-05-27T10:30:00Z"),
            PettyCashCategory.TRANSPORT,
            "Driver Sam",
            4L,
            9L,
            "Courier",
            null
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"id\":\"6500\"");
        assertThat(json).contains("\"tillSessionId\":\"200\"");
        assertThat(json).contains("\"branchId\":\"42\"");
        assertThat(json).contains("\"paidBy\":\"4\"");
        assertThat(json).contains("\"authorisedBy\":\"9\"");
        assertThat(json).contains("\"category\":\"TRANSPORT\"");
        assertThat(json).contains("\"amount\":2000.0000");
        assertThat(json).contains("\"businessDate\":\"2026-05-27\"");
    }
}

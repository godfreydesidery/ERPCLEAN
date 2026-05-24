package com.orbix.engine.modules.pos.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.pos.domain.enums.PosSaleKind;
import com.orbix.engine.modules.pos.domain.enums.PosSaleStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link PosSaleDto}: {@code uid} is the external
 * identifier and {@code id} / all {@code *Id} fields serialise as JSON strings
 * (JSON:API discipline, driven by {@link IdLongAsStringSerializerModifier}).
 * Genuine numerics stay numeric.
 */
class PosSaleDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_id_fields_serialise_as_strings_uid_stays_string() throws Exception {
        PosSaleDto dto = new PosSaleDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            "TILL-1-0001",
            "op-1",
            200L,
            100L,
            5L,
            2L,
            33L,
            540L,
            4L,
            null,
            PosSaleKind.SALE,
            null,
            Instant.parse("2026-05-13T10:00:00Z"),
            Instant.parse("2026-05-13T10:00:01Z"),
            LocalDate.of(2026, 5, 13),
            new BigDecimal("100.0000"),
            new BigDecimal("0.0000"),
            new BigDecimal("18.0000"),
            new BigDecimal("118.0000"),
            new BigDecimal("118.0000"),
            new BigDecimal("0.0000"),
            PosSaleStatus.POSTED,
            null, null, null, null,
            List.of(),
            List.of()
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"tillSessionId\":\"200\"");
        assertThat(json).contains("\"tillId\":\"100\"");
        assertThat(json).contains("\"branchId\":\"5\"");
        assertThat(json).contains("\"companyId\":\"2\"");
        assertThat(json).contains("\"sectionId\":\"33\"");
        assertThat(json).contains("\"customerId\":\"540\"");
        assertThat(json).contains("\"cashierId\":\"4\"");
        // Genuine numerics untouched.
        assertThat(json).contains("\"totalAmount\":118.0000");
    }
}

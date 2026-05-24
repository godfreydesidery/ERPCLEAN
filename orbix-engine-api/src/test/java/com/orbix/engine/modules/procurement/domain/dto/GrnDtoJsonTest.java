package com.orbix.engine.modules.procurement.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.procurement.domain.enums.GrnStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link GrnDto}: {@code uid} is the external
 * identifier and {@code id} / all {@code *Id} fields serialise as JSON strings
 * (JSON:API discipline, driven by {@link IdLongAsStringSerializerModifier}).
 * Genuine numerics stay numeric.
 */
class GrnDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_id_fields_serialise_as_strings_uid_stays_string() throws Exception {
        GrnDto dto = new GrnDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            "GRN-0001",
            2L,
            5L,
            808L,
            17L,
            LocalDate.of(2026, 5, 16),
            "DN-001",
            new BigDecimal("900.0000"),
            new BigDecimal("162.0000"),
            new BigDecimal("1062.0000"),
            GrnStatus.DRAFT,
            null,
            null,
            null,
            List.of()
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"companyId\":\"2\"");
        assertThat(json).contains("\"branchId\":\"5\"");
        assertThat(json).contains("\"supplierId\":\"808\"");
        assertThat(json).contains("\"lpoOrderId\":\"17\"");
        // Genuine numerics untouched.
        assertThat(json).contains("\"totalAmount\":1062.0000");
    }
}

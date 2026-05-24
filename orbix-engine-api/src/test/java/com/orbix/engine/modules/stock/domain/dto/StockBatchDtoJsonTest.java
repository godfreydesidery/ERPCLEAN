package com.orbix.engine.modules.stock.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.stock.domain.enums.StockBatchStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link StockBatchDto}: {@code uid} is the external
 * identifier and {@code id} / all {@code *Id} fields serialise as JSON strings
 * (JSON:API discipline, driven by {@link IdLongAsStringSerializerModifier}).
 * Genuine numerics stay numeric.
 */
class StockBatchDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_id_fields_serialise_as_strings_uid_stays_string() throws Exception {
        StockBatchDto dto = new StockBatchDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            8801L,
            12L,
            7L,
            "B-001",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            new BigDecimal("50.0000"),
            new BigDecimal("48.0000"),
            new BigDecimal("120.0000"),
            "GRN",
            999L,
            StockBatchStatus.ACTIVE
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"itemId\":\"8801\"");
        assertThat(json).contains("\"branchId\":\"12\"");
        assertThat(json).contains("\"companyId\":\"7\"");
        assertThat(json).contains("\"sourceDocId\":\"999\"");
        // Genuine numerics untouched.
        assertThat(json).contains("\"qtyOnHand\":48.0000");
    }
}

package com.orbix.engine.modules.stock.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.stock.domain.enums.StockCountStatus;
import com.orbix.engine.modules.stock.domain.enums.StockCountType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link StockCountDto}: {@code uid} is the external
 * identifier and {@code id} / all {@code *Id} fields serialise as JSON strings
 * (JSON:API discipline, driven by {@link IdLongAsStringSerializerModifier}).
 */
class StockCountDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_id_fields_serialise_as_strings_uid_stays_string() throws Exception {
        StockCountDto dto = new StockCountDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            "SC-0001",
            12L,
            7L,
            LocalDate.of(2026, 5, 14),
            StockCountType.CYCLE,
            StockCountStatus.DRAFT,
            4L,
            null,
            null,
            List.of()
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"branchId\":\"12\"");
        assertThat(json).contains("\"companyId\":\"7\"");
        assertThat(json).contains("\"startedBy\":\"4\"");
    }
}

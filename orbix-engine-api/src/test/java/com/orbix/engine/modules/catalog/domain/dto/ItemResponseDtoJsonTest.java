package com.orbix.engine.modules.catalog.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.domain.enums.WeighingUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link ItemResponseDto} so the JSON:API
 * string-id discipline doesn't silently regress to a numeric id.
 */
class ItemResponseDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void id_is_serialised_as_json_string() throws Exception {
        ItemResponseDto dto = new ItemResponseDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            2L,
            "BR-001",
            "White bread loaf 600g",
            null,
            ItemType.SELLABLE,
            10L,
            20L,
            30L,
            true,
            false,
            null,
            false,
            new BigDecimal("0"),
            new BigDecimal("0"),
            null,
            ItemStatus.ACTIVE
        );

        String json = mapper.writeValueAsString(dto);

        // Primary id is a JSON string (JSON:API discipline).
        assertThat(json).contains("\"id\":\"42\"");
        // uid stays a JSON string as before.
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        // FK references stay numeric until their owning aggregates migrate.
        assertThat(json).contains("\"companyId\":2");
        assertThat(json).contains("\"itemGroupId\":10");
        assertThat(json).contains("\"uomId\":20");
        assertThat(json).contains("\"vatGroupId\":30");
    }

    @Test
    void roundtrip_string_id_in_json_deserialises_to_long() throws Exception {
        String json = """
            {"id":"42","uid":"01HZ8X7M3K9PJK2D7Q5BCN8W4F","companyId":2,"code":"X","name":"X",
             "shortName":null,"type":"SELLABLE","itemGroupId":10,"uomId":20,"vatGroupId":30,
             "tracked":true,"weighed":false,"weighingUnit":null,"batchTracked":false,
             "avgCost":0,"lastCost":0,"minSellPrice":null,"status":"ACTIVE"}
            """;

        ItemResponseDto dto = mapper.readValue(json, ItemResponseDto.class);

        assertThat(dto.id()).isEqualTo(42L);
    }
}

package com.orbix.engine.modules.catalog.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link ItemResponseDto} so the JSON:API
 * string-id discipline doesn't silently regress to a numeric id. The
 * stringification is driven by {@link IdLongAsStringSerializerModifier}
 * (registered via {@code JacksonConfig} at app startup); here we register
 * the same module locally so the test is self-contained.
 */
class ItemResponseDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(
        new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier())
    );

    @Test
    void id_and_all_fk_id_fields_serialise_as_strings() throws Exception {
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

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        // FK / back-reference Long fields are also stringified now.
        assertThat(json).contains("\"companyId\":\"2\"");
        assertThat(json).contains("\"itemGroupId\":\"10\"");
        assertThat(json).contains("\"uomId\":\"20\"");
        assertThat(json).contains("\"vatGroupId\":\"30\"");
        // Genuine numerics (decimals, booleans) untouched.
        assertThat(json).contains("\"avgCost\":0");
        assertThat(json).contains("\"tracked\":true");
    }

    @Test
    void roundtrip_string_id_in_json_deserialises_to_long() throws Exception {
        String json = """
            {"id":"42","uid":"01HZ8X7M3K9PJK2D7Q5BCN8W4F","companyId":"2","code":"X","name":"X",
             "shortName":null,"type":"SELLABLE","itemGroupId":"10","uomId":"20","vatGroupId":"30",
             "tracked":true,"weighed":false,"weighingUnit":null,"batchTracked":false,
             "avgCost":0,"lastCost":0,"minSellPrice":null,"status":"ACTIVE"}
            """;

        ItemResponseDto dto = mapper.readValue(json, ItemResponseDto.class);

        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.companyId()).isEqualTo(2L);
        assertThat(dto.itemGroupId()).isEqualTo(10L);
    }
}

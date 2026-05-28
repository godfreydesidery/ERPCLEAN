package com.orbix.engine.modules.party.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Pin the JSON wire shape of {@link SalesAgentResponseDto}. */
class SalesAgentResponseDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(
        new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier())
    );

    @Test
    void id_and_all_fk_id_fields_serialise_as_strings() throws Exception {
        PartyResponseDto party = new PartyResponseDto(
            88L, "01HZ8X7M3K9PJK2D7Q5BCN8W4F", 2L, "AGT0001",
            "Field Agent", null, PartyCategory.INDIVIDUAL, null, null,
            null, null, null, null, "TZ", null, PartyStatus.ACTIVE);
        SalesAgentResponseDto dto = new SalesAgentResponseDto(
            88L, party, 33L, "AGT1", 5L, new BigDecimal("2.5000"), 11L);

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"partyId\":\"88\"");
        assertThat(json).contains("\"appUserId\":\"33\"");
        assertThat(json).contains("\"routeId\":\"5\"");
        assertThat(json).contains("\"branchId\":\"11\"");
        assertThat(json).contains("\"id\":\"88\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        // Genuine numerics (decimals) untouched
        assertThat(json).contains("\"commissionRate\":2.5000");
    }
}

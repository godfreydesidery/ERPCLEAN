package com.orbix.engine.modules.party.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link CustomerResponseDto} so the JSON:API
 * string-id discipline doesn't silently regress. See {@code ItemResponseDtoJsonTest}
 * for the precedent.
 */
class CustomerResponseDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(
        new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier())
    );

    @Test
    void id_and_all_fk_id_fields_serialise_as_strings() throws Exception {
        PartyResponseDto party = new PartyResponseDto(
            42L, "01HZ8X7M3K9PJK2D7Q5BCN8W4F", 2L, "CUST0001",
            "Mama Sara", null, PartyCategory.BUSINESS, "999-1", null,
            null, null, null, null, "TZ", null, PartyStatus.ACTIVE);
        CustomerResponseDto dto = new CustomerResponseDto(
            42L, party, new BigDecimal("2000000.0000"), 30,
            7L, 9L, 11L, false, false);

        String json = mapper.writeValueAsString(dto);

        // Top-level Long id fields stringify
        assertThat(json).contains("\"partyId\":\"42\"");
        assertThat(json).contains("\"priceListId\":\"7\"");
        assertThat(json).contains("\"defaultSalesAgentId\":\"9\"");
        assertThat(json).contains("\"defaultBranchId\":\"11\"");
        // Embedded party honours the same rule
        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"companyId\":\"2\"");
        // Genuine numerics / booleans untouched
        assertThat(json).contains("\"creditTermsDays\":30");
        assertThat(json).contains("\"walkIn\":false");
        assertThat(json).contains("\"taxExempt\":false");
    }
}

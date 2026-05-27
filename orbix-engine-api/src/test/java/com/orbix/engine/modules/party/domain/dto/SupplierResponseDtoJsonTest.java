package com.orbix.engine.modules.party.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Pin the JSON wire shape of {@link SupplierResponseDto}. */
class SupplierResponseDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(
        new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier())
    );

    @Test
    void id_and_all_fk_id_fields_serialise_as_strings() throws Exception {
        PartyResponseDto party = new PartyResponseDto(
            55L, "01HZ8X7M3K9PJK2D7Q5BCN8W4F", 2L, "SUP0001",
            "Acme Distributors", null, PartyCategory.BUSINESS, "999-1", null,
            null, null, null, null, "TZ", null, PartyStatus.ACTIVE);
        SupplierResponseDto dto = new SupplierResponseDto(
            55L, party, 30, new BigDecimal("0.0000"),
            "TZS", "Stanbic", "0123456789", 7);

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"partyId\":\"55\"");
        assertThat(json).contains("\"id\":\"55\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"companyId\":\"2\"");
        // Genuine numerics untouched
        assertThat(json).contains("\"paymentTermsDays\":30");
        assertThat(json).contains("\"leadTimeDays\":7");
    }
}

package com.orbix.engine.modules.pos.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.pos.domain.enums.TillStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link TillDto}: {@code uid} is the external
 * identifier and {@code id} / all {@code *Id} fields serialise as JSON strings
 * (JSON:API discipline, driven by {@link IdLongAsStringSerializerModifier}).
 */
class TillDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_id_fields_serialise_as_strings_uid_stays_string() throws Exception {
        TillDto dto = new TillDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            2L,
            5L,
            "TILL-1",
            "Main till",
            "INSTALL-9",
            7L,
            TillStatus.ACTIVE
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"companyId\":\"2\"");
        assertThat(json).contains("\"branchId\":\"5\"");
        assertThat(json).contains("\"defaultPriceListId\":\"7\"");
        assertThat(json).contains("\"code\":\"TILL-1\"");
    }
}

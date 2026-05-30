package com.orbix.engine.modules.iam.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the JSON wire shape of {@link UserLookupDto}: {@code id} must serialise
 * as a JSON string (JSON:API discipline enforced globally by
 * {@link IdLongAsStringSerializerModifier}); other fields are plain strings and
 * must not be modified.
 */
class UserLookupDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(
        new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier())
    );

    @Test
    void id_serialises_as_string_not_number() throws Exception {
        UserLookupDto dto = new UserLookupDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            "Alice Kamau",
            "akamau"
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"displayName\":\"Alice Kamau\"");
        assertThat(json).contains("\"username\":\"akamau\"");
        // id must NOT appear as a bare number
        assertThat(json).doesNotContain("\"id\":42");
    }

    @Test
    void roundtrip_string_id_deserialises_to_long() throws Exception {
        String json = """
            {"id":"42","uid":"01HZ8X7M3K9PJK2D7Q5BCN8W4F",
             "displayName":"Alice Kamau","username":"akamau"}
            """;

        UserLookupDto dto = mapper.readValue(json, UserLookupDto.class);

        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.uid()).isEqualTo("01HZ8X7M3K9PJK2D7Q5BCN8W4F");
    }
}

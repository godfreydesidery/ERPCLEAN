package com.orbix.engine.modules.party.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Pin the JSON wire shape of {@link EmployeeResponseDto}. */
class EmployeeResponseDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()))
        .registerModule(new JavaTimeModule());

    @Test
    void id_and_all_fk_id_fields_serialise_as_strings() throws Exception {
        PartyResponseDto party = new PartyResponseDto(
            77L, "01HZ8X7M3K9PJK2D7Q5BCN8W4F", 2L, "EMP0001",
            "Jane Doe", null, PartyCategory.INDIVIDUAL, null, null,
            null, null, null, null, "TZ", null, PartyStatus.ACTIVE);
        EmployeeResponseDto dto = new EmployeeResponseDto(
            77L, party, 33L, "E001", "Cashier", 11L,
            LocalDate.of(2024, 1, 15), null);

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"partyId\":\"77\"");
        assertThat(json).contains("\"appUserId\":\"33\"");
        assertThat(json).contains("\"branchId\":\"11\"");
        assertThat(json).contains("\"id\":\"77\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        // String code field stays as a string
        assertThat(json).contains("\"employeeCode\":\"E001\"");
    }
}

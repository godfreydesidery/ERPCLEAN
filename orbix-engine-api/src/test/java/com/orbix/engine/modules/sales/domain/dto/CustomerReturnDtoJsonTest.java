package com.orbix.engine.modules.sales.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.sales.domain.enums.CustomerReturnStatus;
import com.orbix.engine.modules.sales.domain.enums.ReturnReason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link CustomerReturnDto}: {@code uid} is the
 * external identifier and {@code id} / all {@code *Id} fields serialise as
 * JSON strings (JSON:API discipline, driven by
 * {@link IdLongAsStringSerializerModifier}). Genuine numerics stay numeric.
 */
class CustomerReturnDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_id_fields_serialise_as_strings_uid_stays_string() throws Exception {
        CustomerReturnDto dto = new CustomerReturnDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            "RET-0001",
            2L,
            5L,
            540L,
            9L,
            LocalDate.of(2026, 5, 20),
            ReturnReason.DAMAGED,
            new BigDecimal("1180.0000"),
            CustomerReturnStatus.DRAFT,
            true,
            null, null,
            null,
            List.of()
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"companyId\":\"2\"");
        assertThat(json).contains("\"branchId\":\"5\"");
        assertThat(json).contains("\"customerId\":\"540\"");
        assertThat(json).contains("\"originalInvoiceId\":\"9\"");
        // Genuine numerics untouched.
        assertThat(json).contains("\"totalAmount\":1180.0000");
    }
}

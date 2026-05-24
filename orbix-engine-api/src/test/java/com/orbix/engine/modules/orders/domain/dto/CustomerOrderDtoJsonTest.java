package com.orbix.engine.modules.orders.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderStatus;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link CustomerOrderDto}: {@code uid} is the
 * external identifier and {@code id} / all {@code *Id} fields serialise as JSON
 * strings (JSON:API discipline, driven by
 * {@link IdLongAsStringSerializerModifier}). Genuine numerics stay numeric.
 */
class CustomerOrderDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_id_fields_serialise_as_strings_uid_stays_string() throws Exception {
        CustomerOrderDto dto = new CustomerOrderDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            "ORD-0001",
            2L,
            5L,
            7L,
            540L,
            CustomerOrderType.LAYBY,
            CustomerOrderStatus.DRAFT,
            "TZS",
            null,
            new BigDecimal("200.0000"),
            new BigDecimal("0.0000"),
            new BigDecimal("1000.0000"),
            new BigDecimal("0.0000"),
            new BigDecimal("1000.0000"),
            new BigDecimal("0.0000"),
            new BigDecimal("0.0000"),
            null, null, null, null, null,
            "notes",
            List.of(),
            List.of()
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"companyId\":\"2\"");
        assertThat(json).contains("\"branchId\":\"5\"");
        assertThat(json).contains("\"sectionId\":\"7\"");
        assertThat(json).contains("\"customerId\":\"540\"");
        // Genuine numerics untouched.
        assertThat(json).contains("\"totalAmount\":1000.0000");
        assertThat(json).contains("\"depositRequiredAmount\":200.0000");
    }
}

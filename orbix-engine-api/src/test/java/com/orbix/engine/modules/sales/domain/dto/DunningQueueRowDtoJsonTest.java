package com.orbix.engine.modules.sales.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.sales.domain.enums.AgingBucket;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DunningQueueRowDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void dunningRow_serialisesIdsAsStrings_andEnumsAsStrings() throws Exception {
        DunningQueueRowDto dto = new DunningQueueRowDto(
            10042L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            "Acme Trading",
            new BigDecimal("1000000.0000"),
            new BigDecimal("350000.0000"),
            120,
            LocalDate.of(2026, 1, 28),
            AgingBucket.D_90_PLUS,
            4L
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"customerId\":\"10042\"");
        assertThat(json).contains("\"customerUid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"creditLimit\":1000000.0000");
        assertThat(json).contains("\"totalOutstanding\":350000.0000");
        assertThat(json).contains("\"oldestDaysOverdue\":120");
        assertThat(json).contains("\"oldestDueDate\":\"2026-01-28\"");
        assertThat(json).contains("\"worstBucket\":\"D_90_PLUS\"");
        assertThat(json).contains("\"overdueInvoiceCount\":4");
    }
}

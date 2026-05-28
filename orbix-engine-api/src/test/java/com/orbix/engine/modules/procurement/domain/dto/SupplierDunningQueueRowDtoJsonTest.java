package com.orbix.engine.modules.procurement.domain.dto;

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

/**
 * Pin the JSON wire shape of {@link SupplierDunningQueueRowDto}.
 */
class SupplierDunningQueueRowDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void dunningRow_serialisesIdsAsStrings_andDecimalsNumeric() throws Exception {
        SupplierDunningQueueRowDto dto = new SupplierDunningQueueRowDto(
            30011L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4G",
            "Fast Parts Inc",
            new BigDecimal("95000.0000"),
            95,
            LocalDate.of(2026, 2, 22),
            AgingBucket.D_90_PLUS,
            3L
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"supplierId\":\"30011\"");
        assertThat(json).contains("\"supplierUid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4G\"");
        assertThat(json).contains("\"supplierName\":\"Fast Parts Inc\"");
        assertThat(json).contains("\"totalOutstanding\":95000.0000");
        assertThat(json).contains("\"oldestDaysOverdue\":95");
        assertThat(json).contains("\"oldestDueDate\":\"2026-02-22\"");
        assertThat(json).contains("\"worstBucket\":\"D_90_PLUS\"");
        assertThat(json).contains("\"overdueInvoiceCount\":3");
    }

    @Test
    void dunningRow_nullOldestDueDate_serialisesAsNull() throws Exception {
        SupplierDunningQueueRowDto dto = new SupplierDunningQueueRowDto(
            5L, "01HZ8X7M3K9PJK2D7Q5BCN8W4H", "Current Only",
            new BigDecimal("10000"), null, null, AgingBucket.CURRENT, 0L
        );
        String json = mapper.writeValueAsString(dto);
        assertThat(json).contains("\"oldestDueDate\":null");
        assertThat(json).contains("\"oldestDaysOverdue\":null");
        assertThat(json).contains("\"supplierId\":\"5\"");
    }
}

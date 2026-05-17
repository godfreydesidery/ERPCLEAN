package com.orbix.engine.modules.common.service;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;

/**
 * Serialises Long fields that look like external identifiers ({@code id} or
 * a name ending in {@code Id}) as JSON strings — JSON:API discipline.
 *
 * <p>Why a modifier rather than per-field {@code @JsonSerialize} annotations:
 * the rule is cross-cutting; every DTO follows it by default and new DTOs
 * inherit it for free. Java types stay {@code Long} so internal callers see
 * a typed numeric handle; Jackson coerces back from {@code "42"} to
 * {@code 42L} on deserialisation by default, so request bodies that
 * reference entities by Long id keep working unchanged.
 *
 * <p>Non-id Long fields ({@code version}, {@code totalElements}, counts,
 * timestamps) keep their numeric JSON shape because the name filter
 * requires either exactly {@code id} or a name ending in {@code Id} (camel
 * case — {@code itemGroupId}, {@code companyId}, {@code uomId}, …).
 *
 * <p>Numeric quantities and money ({@code BigDecimal}, {@code int} counts,
 * percentages, prices) are untouched — they're genuinely numeric values,
 * not opaque identifiers.
 */
public class IdLongAsStringSerializerModifier extends BeanSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                     BeanDescription beanDesc,
                                                     List<BeanPropertyWriter> beanProperties) {
        for (BeanPropertyWriter writer : beanProperties) {
            if (isIdLong(writer)) {
                writer.assignSerializer(ToStringSerializer.instance);
            }
        }
        return beanProperties;
    }

    private static boolean isIdLong(BeanPropertyWriter writer) {
        Class<?> raw = writer.getType().getRawClass();
        if (raw != Long.class && raw != long.class) {
            return false;
        }
        String name = writer.getName();
        // exactly "id" or anything ending in "Id" (camelCase: companyId, itemGroupId, ...)
        return "id".equals(name) || name.endsWith("Id");
    }
}

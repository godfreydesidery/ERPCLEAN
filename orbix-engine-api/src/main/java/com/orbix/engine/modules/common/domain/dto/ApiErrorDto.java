package com.orbix.engine.modules.common.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One entry in {@code ApiResponseDto.errors[]}. {@code field} is null for
 * non-field-scoped errors (e.g. business rule violations).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorDto(
    String field,
    String code,
    String message
) {
    public static ApiErrorDto of(String code, String message) {
        return new ApiErrorDto(null, code, message);
    }

    public static ApiErrorDto field(String field, String code, String message) {
        return new ApiErrorDto(field, code, message);
    }
}

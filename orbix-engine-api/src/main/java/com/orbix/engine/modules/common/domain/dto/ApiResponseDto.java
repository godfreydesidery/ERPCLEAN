package com.orbix.engine.modules.common.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Standard response envelope for every REST endpoint. See feedback-api-response-envelope memory.
 *
 * @param status       true on 2xx, false on 4xx/5xx
 * @param statusCode   HTTP status code (200, 201, 401, 409, 422, 500, ...)
 * @param responseCode short business code (e.g. {@code AUTH_LOGIN_OK}, {@code VALIDATION_FAILED})
 * @param message      one-line human summary
 * @param errors       per-field / per-issue error details; empty on success
 * @param data         the actual payload on success; null on error
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponseDto<T>(
    boolean status,
    int statusCode,
    String responseCode,
    String message,
    List<ApiErrorDto> errors,
    T data
) {

    public static <T> ApiResponseDto<T> ok(T data, String responseCode, String message) {
        return new ApiResponseDto<>(true, 200, responseCode, message, List.of(), data);
    }

    public static <T> ApiResponseDto<T> created(T data, String responseCode, String message) {
        return new ApiResponseDto<>(true, 201, responseCode, message, List.of(), data);
    }

    public static <T> ApiResponseDto<T> error(int statusCode, String responseCode, String message, List<ApiErrorDto> errors) {
        return new ApiResponseDto<>(false, statusCode, responseCode, message,
            errors != null ? errors : List.of(), null);
    }

    public static <T> ApiResponseDto<T> error(int statusCode, String responseCode, String message) {
        return error(statusCode, responseCode, message, List.of());
    }
}

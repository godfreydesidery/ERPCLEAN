package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.common.domain.dto.ApiErrorDto;
import com.orbix.engine.modules.common.domain.dto.ApiResponseDto;
import com.orbix.engine.modules.common.domain.enums.ResponseCode;
import com.orbix.engine.modules.day.service.EodBlockedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Maps every uncaught exception to an {@link ApiResponseDto} error envelope.
 * Controllers may still declare their own {@code @ExceptionHandler} for
 * domain-specific business exceptions; this catches everything else.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<Object>> onValidation(MethodArgumentNotValidException ex) {
        List<ApiErrorDto> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(this::toApiError)
            .toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ApiResponseDto.error(422, ResponseCode.VALIDATION_FAILED, "Validation failed", errors)
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDto<Object>> onBadArg(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponseDto.error(400, ResponseCode.BAD_REQUEST, ex.getMessage())
        );
    }

    @ExceptionHandler(EodBlockedException.class)
    public ResponseEntity<ApiResponseDto<Object>> onEodBlocked(EodBlockedException ex) {
        List<ApiErrorDto> errors = ex.blockers().stream()
            .map(b -> ApiErrorDto.field(b.refType() + ":" + b.refId(), b.kind(), b.message()))
            .toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ApiResponseDto.error(422, ResponseCode.DAY_EOD_BLOCKED,
                "Cannot close day — " + errors.size() + " blocker(s)", errors)
        );
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponseDto<Object>> onNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiResponseDto.error(404, ResponseCode.NOT_FOUND, ex.getMessage())
        );
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponseDto<Object>> onRouteNotFound(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiResponseDto.error(404, ResponseCode.NOT_FOUND, "Route not found: " + ex.getRequestURL())
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponseDto<Object>> onUnauthenticated(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiResponseDto.error(401, ResponseCode.UNAUTHORIZED, "Not authenticated")
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponseDto<Object>> onForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ApiResponseDto.error(403, ResponseCode.FORBIDDEN, "Access denied")
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<Object>> onUnknown(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiResponseDto.error(500, ResponseCode.INTERNAL_ERROR, "Internal server error")
        );
    }

    private ApiErrorDto toApiError(FieldError fe) {
        return ApiErrorDto.field(fe.getField(), fe.getCode(), fe.getDefaultMessage());
    }
}

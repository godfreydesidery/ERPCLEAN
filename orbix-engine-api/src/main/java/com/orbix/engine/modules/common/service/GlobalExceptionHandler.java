package com.orbix.engine.modules.common.service;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.orbix.engine.modules.common.domain.dto.ApiErrorDto;
import com.orbix.engine.modules.common.domain.dto.ApiResponseDto;
import com.orbix.engine.modules.common.domain.enums.ResponseCode;
import com.orbix.engine.modules.day.service.EodBlockedException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Maps every uncaught exception to an {@link ApiResponseDto} error envelope.
 * Controllers may still declare their own {@code @ExceptionHandler} for
 * domain-specific business exceptions; this catches everything else.
 *
 * <p>Handler precedence (most-specific first, catch-all last):
 * <ol>
 *   <li>Bean-validation / binding failures → 422</li>
 *   <li>Jackson parse / enum decode failures → 400</li>
 *   <li>Path-variable constraint violations → 400</li>
 *   <li>Type-mismatch path/query variables → 400</li>
 *   <li>IllegalArgumentException (bad caller input) → 400</li>
 *   <li>BusinessPreconditionException (business rule not satisfied) → 422</li>
 *   <li>IllegalStateException (wrong lifecycle state) → 422</li>
 *   <li>ResourceConflictException (duplicate / conflict) → 409</li>
 *   <li>EodBlockedException → 422</li>
 *   <li>NoSuchElementException → 404</li>
 *   <li>NoHandlerFoundException / NoResourceFoundException → 404</li>
 *   <li>AuthenticationException → 401</li>
 *   <li>AccessDeniedException → 403</li>
 *   <li>Exception (catch-all) → 500</li>
 * </ol>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

    // ------------------------------------------------------------------
    // 422 — bean validation
    // ------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<Object>> onValidation(MethodArgumentNotValidException ex) {
        List<ApiErrorDto> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(this::toApiError)
            .toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ApiResponseDto.error(422, ResponseCode.VALIDATION_FAILED, "Validation failed", errors)
        );
    }

    // ------------------------------------------------------------------
    // 400 — malformed JSON body / invalid enum value (ISSUE-CAT-001,
    //        ISSUE-PARTY-001, ISSUE-NFR-001, ISSUE-NEG-ENUM-500-01,
    //        ISSUE-VALIDATION-01)
    // ------------------------------------------------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponseDto<Object>> onBadPayload(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        String message;
        if (cause instanceof InvalidFormatException ife && ife.getTargetType() != null
                && ife.getTargetType().isEnum()) {
            String accepted = Arrays.stream(ife.getTargetType().getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.joining(", "));
            message = "Invalid value '" + ife.getValue() + "' for field '"
                + ife.getPathReference() + "'. Accepted values: [" + accepted + "]";
        } else {
            message = "Malformed request body: "
                + (cause != null ? cause.getMessage() : ex.getMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponseDto.error(400, ResponseCode.BAD_REQUEST, message)
        );
    }

    // ------------------------------------------------------------------
    // 400 — @ValidUlid / @Validated path-variable constraint violations
    //        (ISSUE-VALIDATION-01)
    // ------------------------------------------------------------------

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponseDto<Object>> onConstraintViolation(ConstraintViolationException ex) {
        List<ApiErrorDto> errors = ex.getConstraintViolations().stream()
            .map(cv -> ApiErrorDto.field(cv.getPropertyPath().toString(),
                cv.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                cv.getMessage()))
            .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponseDto.error(400, ResponseCode.BAD_REQUEST, "Invalid request parameter", errors)
        );
    }

    // ------------------------------------------------------------------
    // 400 — type-mismatch on path/query variable (ISSUE-VALIDATION-01)
    // ------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponseDto<Object>> onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponseDto.error(400, ResponseCode.BAD_REQUEST, message)
        );
    }

    // ------------------------------------------------------------------
    // 400 — explicit bad-argument from service layer
    // ------------------------------------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDto<Object>> onBadArg(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponseDto.error(400, ResponseCode.BAD_REQUEST, ex.getMessage())
        );
    }

    // ------------------------------------------------------------------
    // 422 — typed business-precondition exception (preferred throw site)
    //        e.g. DayGuard, GiftCard expiry — carries its own responseCode
    //        (ISSUE-DAY-002, ISSUE-GC-002, ISSUE-POS-002, ISSUE-GC-001)
    // ------------------------------------------------------------------

    @ExceptionHandler(BusinessPreconditionException.class)
    public ResponseEntity<ApiResponseDto<Object>> onPrecondition(BusinessPreconditionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ApiResponseDto.error(422, ex.getResponseCode(), ex.getMessage())
        );
    }

    // ------------------------------------------------------------------
    // 422 — untyped IllegalStateException (wrong lifecycle / precondition)
    //        kept as safety-net so callers that were already throwing
    //        IllegalStateException get a 422 instead of 500
    //        (ISSUE-DAY-002, ISSUE-GC-002, ISSUE-PROC-002)
    // ------------------------------------------------------------------

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponseDto<Object>> onIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ApiResponseDto.error(422, ResponseCode.PRECONDITION_FAILED, ex.getMessage())
        );
    }

    // ------------------------------------------------------------------
    // 409 — resource-conflict / duplicate state (ISSUE-DAY-001)
    // ------------------------------------------------------------------

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiResponseDto<Object>> onConflict(ResourceConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiResponseDto.error(409, ResponseCode.CONFLICT, ex.getMessage())
        );
    }

    // ------------------------------------------------------------------
    // 422 — EOD blocked (existing)
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // 404 — entity not found
    // ------------------------------------------------------------------

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponseDto<Object>> onNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiResponseDto.error(404, ResponseCode.NOT_FOUND, ex.getMessage())
        );
    }

    // ------------------------------------------------------------------
    // 404 — unknown route (ISSUE-PROC-003, ISSUE-CASH-001)
    //        NoHandlerFoundException: raised when spring.mvc.throw-exception-if-no-handler-found=true
    //        NoResourceFoundException: Spring Boot 3.2+ for static-resource miss
    // ------------------------------------------------------------------

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponseDto<Object>> onRouteNotFound(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiResponseDto.error(404, ResponseCode.NOT_FOUND, "Route not found: " + ex.getRequestURL())
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponseDto<Object>> onResourceNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiResponseDto.error(404, ResponseCode.NOT_FOUND,
                "Route not found: " + ex.getResourcePath())
        );
    }

    // ------------------------------------------------------------------
    // 401 / 403 — security (existing; note: unauthenticated->401 is also
    //             handled at the AuthenticationEntryPoint in SecurityConfig
    //             for requests that never reach a controller)
    // ------------------------------------------------------------------

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

    /**
     * ResponseStatusException carries an embedded HTTP status (e.g. 426 from
     * SyncController.validateContractVersion). Without this handler the catch-all
     * below swallows the status and returns 500 instead.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponseDto<Object>> onResponseStatus(ResponseStatusException ex) {
        int code = ex.getStatusCode().value();
        String rc = switch (code) {
            case 404 -> ResponseCode.NOT_FOUND;
            case 409 -> ResponseCode.SYNC_CONTRACT_TOO_NEW;
            case 426 -> ResponseCode.SYNC_CONTRACT_TOO_OLD;
            default  -> ResponseCode.BAD_REQUEST;
        };
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode()).body(
            ApiResponseDto.error(code, rc, reason)
        );
    }

    // ------------------------------------------------------------------
    // 500 — catch-all (must remain last)
    // ------------------------------------------------------------------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<Object>> onUnknown(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiResponseDto.error(500, ResponseCode.INTERNAL_ERROR, "Internal server error")
        );
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private ApiErrorDto toApiError(FieldError fe) {
        return ApiErrorDto.field(fe.getField(), fe.getCode(), fe.getDefaultMessage());
    }
}

package com.orbix.engine.modules.stock.domain.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ISSUE-XFER-001: verifies that {@code @Valid} on the {@code lines} field
 * causes Bean Validation to cascade into each {@link CreateStockTransferRequestDto.TransferLine},
 * so a null {@code itemId} or non-positive {@code issuedQty} is rejected at
 * the binding layer (HTTP 400/422) rather than propagating to the DB (HTTP 500).
 */
class CreateStockTransferRequestDtoValidationTest {

    private static final Validator VALIDATOR =
        Validation.buildDefaultValidatorFactory().getValidator();

    private static final BigDecimal VALID_QTY = new BigDecimal("5");
    private static final Long VALID_ITEM = 1L;

    @Test
    void validDto_passesWithNoViolations() {
        var dto = new CreateStockTransferRequestDto(
            "XFER-001", 1L, 2L,
            List.of(new CreateStockTransferRequestDto.TransferLine(VALID_ITEM, VALID_QTY)));

        assertThat(VALIDATOR.validate(dto)).isEmpty();
    }

    @Test
    void nullItemId_in_line_is_rejected() {
        var dto = new CreateStockTransferRequestDto(
            "XFER-002", 1L, 2L,
            List.of(new CreateStockTransferRequestDto.TransferLine(null, VALID_QTY)));

        Set<ConstraintViolation<CreateStockTransferRequestDto>> violations = VALIDATOR.validate(dto);

        assertThat(violations).isNotEmpty();
        Set<String> paths = violations.stream()
            .map(v -> v.getPropertyPath().toString())
            .collect(Collectors.toSet());
        assertThat(paths).anyMatch(p -> p.contains("itemId"));
    }

    @Test
    void zeroIssuedQty_in_line_is_rejected() {
        var dto = new CreateStockTransferRequestDto(
            "XFER-003", 1L, 2L,
            List.of(new CreateStockTransferRequestDto.TransferLine(VALID_ITEM, BigDecimal.ZERO)));

        Set<ConstraintViolation<CreateStockTransferRequestDto>> violations = VALIDATOR.validate(dto);

        assertThat(violations).isNotEmpty();
        Set<String> paths = violations.stream()
            .map(v -> v.getPropertyPath().toString())
            .collect(Collectors.toSet());
        assertThat(paths).anyMatch(p -> p.contains("issuedQty"));
    }

    @Test
    void negativeIssuedQty_in_line_is_rejected() {
        var dto = new CreateStockTransferRequestDto(
            "XFER-004", 1L, 2L,
            List.of(new CreateStockTransferRequestDto.TransferLine(VALID_ITEM, new BigDecimal("-1"))));

        Set<ConstraintViolation<CreateStockTransferRequestDto>> violations = VALIDATOR.validate(dto);

        assertThat(violations).isNotEmpty();
        Set<String> paths = violations.stream()
            .map(v -> v.getPropertyPath().toString())
            .collect(Collectors.toSet());
        assertThat(paths).anyMatch(p -> p.contains("issuedQty"));
    }
}

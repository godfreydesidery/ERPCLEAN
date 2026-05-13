package com.orbix.engine.modules.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequestDto(
    @NotBlank String refreshToken
) {}

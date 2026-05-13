package com.orbix.engine.modules.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(max = 80) String username,
    @NotBlank @Size(max = 120) String password
) {}

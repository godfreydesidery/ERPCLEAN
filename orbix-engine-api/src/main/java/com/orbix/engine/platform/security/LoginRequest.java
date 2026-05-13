package com.orbix.engine.platform.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(max = 80) String username,
    @NotBlank @Size(max = 120) String password
) {}

package com.orbix.engine.modules.iam.domain.dto;

import jakarta.validation.constraints.NotNull;

/** Switches the caller's active / default branch. */
public record SetActiveBranchRequestDto(
    @NotNull Long branchId
) {}

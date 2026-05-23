package com.orbix.engine.modules.iam.domain.dto;

import java.util.List;

/** A page of users for the admin user list (server-side pagination + search/filter). */
public record UserPageDto(
    List<UserSummaryDto> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}

package com.orbix.engine.modules.auth.service;

import java.util.List;

/**
 * Issues and parses access JWTs. The local-dev impl uses HS256;
 * production deployments switch to RS256 with key rotation
 * (see ARCHITECTURE.md §2.5 and §7.1 / §8).
 */
public interface JwtService {

    String issueAccessToken(long userId, long companyId, Long branchId, List<String> privileges);

    Claims parse(String token);

    record Claims(Long userId, Long companyId, Long branchId, List<String> privileges) {}
}

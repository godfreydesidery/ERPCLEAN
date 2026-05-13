package com.orbix.engine.platform.security;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Holds the resolved identity / scope for the current request.
 * Populated by {@link JwtAuthenticationFilter}; consumed by services
 * and repositories that need company / branch filtering.
 */
@Component
@RequestScope
public class RequestContext {

    private Long userId;
    private Long companyId;
    private Long branchId;
    private String clientVersion;

    public void bind(Long userId, Long companyId, Long branchId, String clientVersion) {
        this.userId = userId;
        this.companyId = companyId;
        this.branchId = branchId;
        this.clientVersion = clientVersion;
    }

    public void clear() {
        this.userId = null;
        this.companyId = null;
        this.branchId = null;
        this.clientVersion = null;
    }

    public Long userId() { return userId; }
    public Long companyId() { return companyId; }
    public Long branchId() { return branchId; }
    public String clientVersion() { return clientVersion; }

    public Long requireBranchId() {
        if (branchId == null) {
            throw new IllegalStateException("Branch context required but not set");
        }
        return branchId;
    }
}

package com.orbix.engine.modules.common.service;

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
    private String ip;
    private String jti;

    public void bind(Long userId, Long companyId, Long branchId, String clientVersion, String ip, String jti) {
        this.userId = userId;
        this.companyId = companyId;
        this.branchId = branchId;
        this.clientVersion = clientVersion;
        this.ip = ip;
        this.jti = jti;
    }

    /** Capture transport metadata for anonymous requests (e.g. login) that have no identity yet. */
    public void bindClient(String clientVersion, String ip) {
        this.clientVersion = clientVersion;
        this.ip = ip;
    }

    public void clear() {
        this.userId = null;
        this.companyId = null;
        this.branchId = null;
        this.clientVersion = null;
        this.ip = null;
        this.jti = null;
    }

    public Long userId() { return userId; }
    public Long companyId() { return companyId; }
    public Long branchId() { return branchId; }
    public String clientVersion() { return clientVersion; }
    public String ip() { return ip; }
    public String jti() { return jti; }

    public Long requireBranchId() {
        if (branchId == null) {
            throw new IllegalStateException("Branch context required but not set");
        }
        return branchId;
    }
}

package com.orbix.engine.modules.iam.service;

import com.orbix.engine.modules.iam.domain.RootAdmin;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Central protection for the {@link RootAdmin} account. Consulted by the user
 * and role admin services to reject any mutation that could change rootadmin or
 * cut off its access (disable, branch assignment, password reset, role
 * grant/revoke). rootadmin's password is changed only via the bootstrap env +
 * the token-gated reset endpoint.
 */
@Component
public class RootAdminGuard {

    public boolean isRootAdmin(AppUser user) {
        return user != null && RootAdmin.is(user.getUsername());
    }

    /**
     * Rejects a mutation targeting rootadmin. {@code action} completes the
     * sentence "the rootadmin account cannot be …".
     */
    public void assertMutable(AppUser user, String action) {
        if (isRootAdmin(user)) {
            throw new AccessDeniedException("The rootadmin account is protected and cannot be " + action);
        }
    }
}

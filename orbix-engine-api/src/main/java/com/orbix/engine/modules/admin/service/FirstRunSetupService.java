package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.FirstRunRequestDto;
import com.orbix.engine.modules.admin.domain.dto.FirstRunResponseDto;

/**
 * Bootstraps an empty deployment. Creates organisation + company + first
 * branch + default RETAIL_FLOOR section + functional currency + admin user
 * in a single transaction. See US-COMP-001 and PRD §5.2.
 *
 * <p>Idempotent on company code: a re-run with the same payload returns the
 * existing IDs; with a different payload returns 409.
 */
public interface FirstRunSetupService {

    FirstRunResponseDto bootstrap(FirstRunRequestDto request);

    /** Returns true if any organisation exists — the wizard should redirect to /login. */
    boolean isBootstrapped();

    class AlreadyBootstrappedException extends RuntimeException {
        public AlreadyBootstrappedException(String message) { super(message); }
    }
}

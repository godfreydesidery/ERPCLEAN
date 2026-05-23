package com.orbix.engine.modules.iam.service;

/**
 * Central password-strength policy (US-IAM-005). Enforces a minimum length and
 * rejects well-known / leaked passwords. Applied wherever a user-chosen
 * password is accepted (self-service change, admin-supplied create / reset).
 */
public interface PasswordPolicyService {

    /**
     * Validate a plaintext password against policy.
     *
     * @throws IllegalArgumentException if it is too short or appears in the
     *         common-password list.
     */
    void validate(String rawPassword);
}

package com.orbix.engine.modules.iam.service;

import com.orbix.engine.modules.iam.domain.dto.ChangePasswordRequestDto;
import com.orbix.engine.modules.iam.domain.dto.CreateUserRequestDto;
import com.orbix.engine.modules.iam.domain.dto.CreateUserResponseDto;
import com.orbix.engine.modules.iam.domain.dto.ResetPasswordRequestDto;
import com.orbix.engine.modules.iam.domain.dto.ResetPasswordResponseDto;
import com.orbix.engine.modules.iam.domain.dto.UpdateUserRequestDto;
import com.orbix.engine.modules.iam.domain.dto.UserDetailDto;
import com.orbix.engine.modules.iam.domain.dto.UserLookupDto;
import com.orbix.engine.modules.iam.domain.dto.UserPageDto;

import java.util.List;

/**
 * Day-2 user administration (F0.4c). Companion to {@link RoleAdminService} —
 * that one assigns roles to existing users; this one creates / updates the
 * users themselves.
 */
public interface UserAdminService {

    /**
     * Minimal name-search for picker fields — accessible to any authenticated user.
     * Returns only ACTIVE users in the caller's company. Cap is enforced by the impl.
     */
    List<UserLookupDto> lookupUsers(String q, int size);

    /** Server-side paginated user list. {@code q} = search; {@code statusFilter} = all|active|disabled|locked|reset. */
    UserPageDto listUsers(String q, String statusFilter, int page, int size);

    UserDetailDto getUserByUid(String uid);

    /** Admin-issued create. Returns the new user + any server-generated temp password. */
    CreateUserResponseDto createUser(CreateUserRequestDto request);

    UserDetailDto updateUserByUid(String uid, UpdateUserRequestDto request);

    /** Admin-issued password reset. Returns any server-generated temp password. */
    ResetPasswordResponseDto resetPasswordByUid(String uid, ResetPasswordRequestDto request);

    UserDetailDto disableUserByUid(String uid);

    UserDetailDto enableUserByUid(String uid);

    /** Clears the lock-out timer without changing user status. */
    UserDetailDto unlockUserByUid(String uid);

    /** Revokes every active refresh token for the user — kills all sessions everywhere. */
    void forceLogoutByUid(String uid);

    /** Self-service password change — must supply current password. */
    void changeMyPassword(ChangePasswordRequestDto request);
}

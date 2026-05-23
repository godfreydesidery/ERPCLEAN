package com.orbix.engine.modules.iam.service;

import com.orbix.engine.modules.iam.domain.dto.ChangePasswordRequestDto;
import com.orbix.engine.modules.iam.domain.dto.CreateUserRequestDto;
import com.orbix.engine.modules.iam.domain.dto.CreateUserResponseDto;
import com.orbix.engine.modules.iam.domain.dto.ResetPasswordRequestDto;
import com.orbix.engine.modules.iam.domain.dto.ResetPasswordResponseDto;
import com.orbix.engine.modules.iam.domain.dto.UpdateUserRequestDto;
import com.orbix.engine.modules.iam.domain.dto.UserDetailDto;
import com.orbix.engine.modules.iam.domain.dto.UserSummaryDto;

import java.util.List;

/**
 * Day-2 user administration (F0.4c). Companion to {@link RoleAdminService} —
 * that one assigns roles to existing users; this one creates / updates the
 * users themselves.
 */
public interface UserAdminService {

    List<UserSummaryDto> listUsers();

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

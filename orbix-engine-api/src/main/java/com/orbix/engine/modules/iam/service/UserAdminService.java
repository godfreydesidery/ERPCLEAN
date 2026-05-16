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

    UserDetailDto getUser(Long userId);

    /** Admin-issued create. Returns the new user + any server-generated temp password. */
    CreateUserResponseDto createUser(CreateUserRequestDto request);

    UserDetailDto updateUser(Long userId, UpdateUserRequestDto request);

    /** Admin-issued password reset. Returns any server-generated temp password. */
    ResetPasswordResponseDto resetPassword(Long userId, ResetPasswordRequestDto request);

    UserDetailDto disableUser(Long userId);

    UserDetailDto enableUser(Long userId);

    /** Clears the lock-out timer without changing user status. */
    UserDetailDto unlockUser(Long userId);

    /** Revokes every active refresh token for the user — kills all sessions everywhere. */
    void forceLogout(Long userId);

    /** Self-service password change — must supply current password. */
    void changeMyPassword(ChangePasswordRequestDto request);
}

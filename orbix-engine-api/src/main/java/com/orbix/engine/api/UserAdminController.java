package com.orbix.engine.api;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.iam.domain.dto.ChangePasswordRequestDto;
import com.orbix.engine.modules.iam.domain.dto.CreateUserRequestDto;
import com.orbix.engine.modules.iam.domain.dto.CreateUserResponseDto;
import com.orbix.engine.modules.iam.domain.dto.ResetPasswordRequestDto;
import com.orbix.engine.modules.iam.domain.dto.ResetPasswordResponseDto;
import com.orbix.engine.modules.iam.domain.dto.UpdateUserRequestDto;
import com.orbix.engine.modules.iam.domain.dto.UserDetailDto;
import com.orbix.engine.modules.iam.domain.dto.UserPageDto;
import com.orbix.engine.modules.iam.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Day-2 user administration (F0.4c). Companion to {@link RoleAdminController}.
 * Users are addressed by {@code uid}; all endpoints except the self-service
 * password change require {@code IAM.MANAGE_USERS}.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserAdminController {

    private final UserAdminService service;

    @GetMapping
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public UserPageDto listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.listUsers(q, status, page, size);
    }

    @GetMapping("/uid/{uid}")
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public UserDetailDto getUser(@PathVariable @ValidUlid String uid) {
        return service.getUserByUid(uid);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public ResponseEntity<CreateUserResponseDto> createUser(
            @Valid @RequestBody CreateUserRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createUser(request));
    }

    @PatchMapping("/uid/{uid}")
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public UserDetailDto updateUser(@PathVariable @ValidUlid String uid,
                                    @Valid @RequestBody UpdateUserRequestDto request) {
        return service.updateUserByUid(uid, request);
    }

    @PostMapping("/uid/{uid}/reset-password")
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public ResetPasswordResponseDto resetPassword(@PathVariable @ValidUlid String uid,
                                                  @Valid @RequestBody ResetPasswordRequestDto request) {
        return service.resetPasswordByUid(uid, request);
    }

    @PostMapping("/uid/{uid}/disable")
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public UserDetailDto disableUser(@PathVariable @ValidUlid String uid) {
        return service.disableUserByUid(uid);
    }

    @PostMapping("/uid/{uid}/enable")
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public UserDetailDto enableUser(@PathVariable @ValidUlid String uid) {
        return service.enableUserByUid(uid);
    }

    @PostMapping("/uid/{uid}/unlock")
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public UserDetailDto unlockUser(@PathVariable @ValidUlid String uid) {
        return service.unlockUserByUid(uid);
    }

    @PostMapping("/uid/{uid}/force-logout")
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public ResponseEntity<Void> forceLogout(@PathVariable @ValidUlid String uid) {
        service.forceLogoutByUid(uid);
        return ResponseEntity.noContent().build();
    }

    /** Self-service password change — every authenticated user can call this. */
    @PostMapping("/me/change-password")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody ChangePasswordRequestDto request) {
        service.changeMyPassword(request);
        return ResponseEntity.noContent().build();
    }
}

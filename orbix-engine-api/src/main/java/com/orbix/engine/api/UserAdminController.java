package com.orbix.engine.api;

import com.orbix.engine.modules.iam.domain.dto.ChangePasswordRequestDto;
import com.orbix.engine.modules.iam.domain.dto.CreateUserRequestDto;
import com.orbix.engine.modules.iam.domain.dto.CreateUserResponseDto;
import com.orbix.engine.modules.iam.domain.dto.ResetPasswordRequestDto;
import com.orbix.engine.modules.iam.domain.dto.ResetPasswordResponseDto;
import com.orbix.engine.modules.iam.domain.dto.UpdateUserRequestDto;
import com.orbix.engine.modules.iam.domain.dto.UserDetailDto;
import com.orbix.engine.modules.iam.domain.dto.UserSummaryDto;
import com.orbix.engine.modules.iam.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Day-2 user administration (F0.4c). Companion to {@link RoleAdminController}.
 * All endpoints except the self-service password change require
 * {@code IAM.MANAGE_USERS}.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService service;

    @GetMapping
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public List<UserSummaryDto> listUsers() {
        return service.listUsers();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public UserDetailDto getUser(@PathVariable("id") Long id) {
        return service.getUser(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public ResponseEntity<CreateUserResponseDto> createUser(
            @Valid @RequestBody CreateUserRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createUser(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public UserDetailDto updateUser(@PathVariable("id") Long id,
                                    @Valid @RequestBody UpdateUserRequestDto request) {
        return service.updateUser(id, request);
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public ResetPasswordResponseDto resetPassword(@PathVariable("id") Long id,
                                                  @Valid @RequestBody ResetPasswordRequestDto request) {
        return service.resetPassword(id, request);
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public UserDetailDto disableUser(@PathVariable("id") Long id) {
        return service.disableUser(id);
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public UserDetailDto enableUser(@PathVariable("id") Long id) {
        return service.enableUser(id);
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public UserDetailDto unlockUser(@PathVariable("id") Long id) {
        return service.unlockUser(id);
    }

    @PostMapping("/{id}/force-logout")
    @PreAuthorize("hasAuthority('IAM.MANAGE_USERS')")
    public ResponseEntity<Void> forceLogout(@PathVariable("id") Long id) {
        service.forceLogout(id);
        return ResponseEntity.noContent().build();
    }

    /** Self-service password change — every authenticated user can call this. */
    @PostMapping("/me/change-password")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody ChangePasswordRequestDto request) {
        service.changeMyPassword(request);
        return ResponseEntity.noContent().build();
    }
}

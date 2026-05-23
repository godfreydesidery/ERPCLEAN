package com.orbix.engine.api;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.iam.domain.dto.CreateRoleRequestDto;
import com.orbix.engine.modules.iam.domain.dto.GrantRoleRequestDto;
import com.orbix.engine.modules.iam.domain.dto.PermissionDto;
import com.orbix.engine.modules.iam.domain.dto.RoleDetailDto;
import com.orbix.engine.modules.iam.domain.dto.RoleGrantDto;
import com.orbix.engine.modules.iam.domain.dto.RoleSummaryDto;
import com.orbix.engine.modules.iam.domain.dto.SetRolePermissionsRequestDto;
import com.orbix.engine.modules.iam.domain.dto.UpdateRoleRequestDto;
import com.orbix.engine.modules.iam.service.RoleAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Role / permission administration (US-IAM-008, US-IAM-009). Roles and grants
 * are addressed by {@code uid}. Every endpoint is gated by {@code IAM.MANAGE_ROLES}.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('IAM.MANAGE_ROLES')")
@Validated
public class RoleAdminController {

    private final RoleAdminService service;

    @GetMapping("/permissions")
    public List<PermissionDto> listPermissions() {
        return service.listPermissions();
    }

    @GetMapping("/roles")
    public List<RoleSummaryDto> listRoles() {
        return service.listRoles();
    }

    @GetMapping("/roles/uid/{uid}")
    public RoleDetailDto getRole(@PathVariable @ValidUlid String uid) {
        return service.getRoleByUid(uid);
    }

    @PostMapping("/roles")
    public ResponseEntity<RoleDetailDto> createRole(@Valid @RequestBody CreateRoleRequestDto request) {
        RoleDetailDto role = service.createRole(request);
        return ResponseEntity.created(URI.create("/api/v1/roles/uid/" + role.uid())).body(role);
    }

    @PatchMapping("/roles/uid/{uid}")
    public RoleDetailDto updateRole(@PathVariable @ValidUlid String uid,
                                    @Valid @RequestBody UpdateRoleRequestDto request) {
        return service.updateRoleByUid(uid, request);
    }

    @PutMapping("/roles/uid/{uid}/permissions")
    public RoleDetailDto setPermissions(@PathVariable @ValidUlid String uid,
                                        @Valid @RequestBody SetRolePermissionsRequestDto request) {
        return service.setPermissionsByUid(uid, request);
    }

    @DeleteMapping("/roles/uid/{uid}")
    public ResponseEntity<Void> deleteRole(@PathVariable @ValidUlid String uid) {
        service.deleteRoleByUid(uid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roles/uid/{uid}/grants")
    public List<RoleGrantDto> listGrants(@PathVariable @ValidUlid String uid) {
        return service.listGrantsByUid(uid);
    }

    @PostMapping("/roles/uid/{uid}/grants")
    public ResponseEntity<RoleGrantDto> grantRole(@PathVariable @ValidUlid String uid,
                                                  @Valid @RequestBody GrantRoleRequestDto request) {
        RoleGrantDto grant = service.grantRoleByUid(uid, request);
        return ResponseEntity.created(URI.create("/api/v1/grants/uid/" + grant.uid())).body(grant);
    }

    @DeleteMapping("/grants/uid/{grantUid}")
    public ResponseEntity<Void> revokeGrant(@PathVariable @ValidUlid String grantUid) {
        service.revokeGrantByUid(grantUid);
        return ResponseEntity.noContent().build();
    }
}

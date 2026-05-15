package com.orbix.engine.api;

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
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Role / permission administration (US-IAM-008, US-IAM-009). Every endpoint is
 * gated by {@code IAM.MANAGE_ROLES}. Backs the web RoleAdminComponent.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('IAM.MANAGE_ROLES')")
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

    @GetMapping("/roles/{id}")
    public RoleDetailDto getRole(@PathVariable Long id) {
        return service.getRole(id);
    }

    @PostMapping("/roles")
    public ResponseEntity<RoleDetailDto> createRole(@Valid @RequestBody CreateRoleRequestDto request) {
        RoleDetailDto role = service.createRole(request);
        return ResponseEntity.created(URI.create("/api/v1/roles/" + role.id())).body(role);
    }

    @PatchMapping("/roles/{id}")
    public RoleDetailDto updateRole(@PathVariable Long id,
                                    @Valid @RequestBody UpdateRoleRequestDto request) {
        return service.updateRole(id, request);
    }

    @PutMapping("/roles/{id}/permissions")
    public RoleDetailDto setPermissions(@PathVariable Long id,
                                        @Valid @RequestBody SetRolePermissionsRequestDto request) {
        return service.setPermissions(id, request);
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        service.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roles/{id}/grants")
    public List<RoleGrantDto> listGrants(@PathVariable Long id) {
        return service.listGrants(id);
    }

    @PostMapping("/roles/{id}/grants")
    public ResponseEntity<RoleGrantDto> grantRole(@PathVariable Long id,
                                                  @Valid @RequestBody GrantRoleRequestDto request) {
        RoleGrantDto grant = service.grantRole(id, request);
        return ResponseEntity.created(URI.create("/api/v1/grants/" + grant.id())).body(grant);
    }

    @DeleteMapping("/grants/{grantId}")
    public ResponseEntity<Void> revokeGrant(@PathVariable Long grantId) {
        service.revokeGrant(grantId);
        return ResponseEntity.noContent().build();
    }
}

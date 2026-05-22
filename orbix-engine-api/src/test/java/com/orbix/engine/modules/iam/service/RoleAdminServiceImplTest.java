package com.orbix.engine.modules.iam.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.domain.dto.CreateRoleRequestDto;
import com.orbix.engine.modules.iam.domain.dto.GrantRoleRequestDto;
import com.orbix.engine.modules.iam.domain.dto.RoleDetailDto;
import com.orbix.engine.modules.iam.domain.dto.RoleGrantDto;
import com.orbix.engine.modules.iam.domain.dto.SetRolePermissionsRequestDto;
import com.orbix.engine.modules.iam.domain.dto.UpdateRoleRequestDto;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.domain.entity.Permission;
import com.orbix.engine.modules.iam.domain.entity.Role;
import com.orbix.engine.modules.iam.domain.entity.UserRole;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import com.orbix.engine.modules.iam.repository.PermissionRepository;
import com.orbix.engine.modules.iam.repository.RoleRepository;
import com.orbix.engine.modules.iam.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAdminServiceImplTest {

    private static final Long ACTOR_ID = 7L;
    private static final Long COMPANY_ID = 3L;

    @Mock private RoleRepository roles;
    @Mock private PermissionRepository permissions;
    @Mock private UserRoleRepository userRoles;
    @Mock private AppUserRepository users;
    @Mock private RequestContext context;

    @InjectMocks private RoleAdminServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
    }

    private static Role role(Long id, String code, boolean system) {
        Role role = new Role(code, "Name " + code, "desc", system, ACTOR_ID);
        role.setId(id);
        return role;
    }

    private static Permission permission(Long id, String code, String module) {
        Permission permission = new Permission(code, "desc " + code, module);
        permission.setId(id);
        return permission;
    }

    // ---- createRole -------------------------------------------------------

    @Test
    void createRole_uppercasesCode_savesNonSystemRole() {
        when(roles.existsByCode("SALES_MANAGER")).thenReturn(false);
        when(roles.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            r.setId(42L);
            return r;
        });

        RoleDetailDto result = service.createRole(
            new CreateRoleRequestDto(" sales_manager ", "Sales manager", "runs sales"));

        ArgumentCaptor<Role> saved = ArgumentCaptor.forClass(Role.class);
        verify(roles).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("SALES_MANAGER");
        assertThat(saved.getValue().isSystem()).isFalse();
        assertThat(result.code()).isEqualTo("SALES_MANAGER");
        assertThat(result.id()).isEqualTo(42L);
    }

    @Test
    void createRole_rejectsDuplicateCode() {
        when(roles.existsByCode("ADMIN")).thenReturn(true);

        assertThatThrownBy(() -> service.createRole(
            new CreateRoleRequestDto("admin", "Admin", "")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(roles, never()).save(any());
    }

    // ---- getRole / updateRole --------------------------------------------

    @Test
    void getRole_notFound_throwsNoSuchElement() {
        when(roles.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRole(99L))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updateRole_updatesDetails() {
        Role existing = role(5L, "BUYER", false);
        when(roles.findById(5L)).thenReturn(Optional.of(existing));
        when(roles.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleDetailDto result = service.updateRole(5L,
            new UpdateRoleRequestDto("Senior buyer", "updated desc"));

        assertThat(result.name()).isEqualTo("Senior buyer");
        assertThat(existing.getName()).isEqualTo("Senior buyer");
        assertThat(existing.getDescription()).isEqualTo("updated desc");
    }

    @Test
    void updateRole_rejectsSystemRole() {
        Role system = role(1L, "ADMIN", true);
        when(roles.findById(1L)).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> service.updateRole(1L,
            new UpdateRoleRequestDto("x", "y")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("System role");
        verify(roles, never()).save(any());
    }

    // ---- setPermissions ---------------------------------------------------

    @Test
    void setPermissions_replacesRolePermissionSet() {
        Role existing = role(5L, "BUYER", false);
        Permission p1 = permission(10L, "ITEM.CREATE", "catalog");
        Permission p2 = permission(11L, "ITEM.UPDATE", "catalog");
        when(roles.findById(5L)).thenReturn(Optional.of(existing));
        when(permissions.findAllById(List.of(10L, 11L))).thenReturn(List.of(p1, p2));
        when(roles.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleDetailDto result = service.setPermissions(5L,
            new SetRolePermissionsRequestDto(List.of(10L, 11L)));

        assertThat(result.permissions()).extracting(dto -> dto.code())
            .containsExactlyInAnyOrder("ITEM.CREATE", "ITEM.UPDATE");
        assertThat(existing.getPermissions()).hasSize(2);
    }

    @Test
    void setPermissions_rejectsUnknownPermissionId() {
        Role existing = role(5L, "BUYER", false);
        when(roles.findById(5L)).thenReturn(Optional.of(existing));
        when(permissions.findAllById(List.of(10L, 999L)))
            .thenReturn(List.of(permission(10L, "ITEM.CREATE", "catalog")));

        assertThatThrownBy(() -> service.setPermissions(5L,
            new SetRolePermissionsRequestDto(List.of(10L, 999L))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid");
        verify(roles, never()).save(any());
    }

    @Test
    void setPermissions_rejectsSystemRole() {
        when(roles.findById(1L)).thenReturn(Optional.of(role(1L, "ADMIN", true)));

        assertThatThrownBy(() -> service.setPermissions(1L,
            new SetRolePermissionsRequestDto(List.of(10L))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- deleteRole -------------------------------------------------------

    @Test
    void deleteRole_deletesWhenNoActiveGrants() {
        Role existing = role(5L, "BUYER", false);
        when(roles.findById(5L)).thenReturn(Optional.of(existing));
        when(userRoles.existsByRoleIdAndRevokedAtIsNull(5L)).thenReturn(false);

        service.deleteRole(5L);

        verify(roles).delete(existing);
    }

    @Test
    void deleteRole_blockedWhenActiveGrantsExist() {
        Role existing = role(5L, "BUYER", false);
        when(roles.findById(5L)).thenReturn(Optional.of(existing));
        when(userRoles.existsByRoleIdAndRevokedAtIsNull(5L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteRole(5L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("active grants");
        verify(roles, never()).delete(any());
    }

    @Test
    void deleteRole_rejectsSystemRole() {
        when(roles.findById(1L)).thenReturn(Optional.of(role(1L, "ADMIN", true)));

        assertThatThrownBy(() -> service.deleteRole(1L))
            .isInstanceOf(IllegalArgumentException.class);
        verify(roles, never()).delete(any());
    }

    // ---- grantRole --------------------------------------------------------

    @Test
    void grantRole_createsCompanyScopedGrant() {
        Role existing = role(5L, "BUYER", false);
        AppUser user = new AppUser("jdoe", "hash", "Jane Doe", COMPANY_ID, null, ACTOR_ID);
        user.setId(20L);
        when(roles.findById(5L)).thenReturn(Optional.of(existing));
        when(users.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(userRoles.findByUserIdAndCompanyIdAndRevokedAtIsNull(20L, COMPANY_ID))
            .thenReturn(List.of());
        when(userRoles.save(any(UserRole.class))).thenAnswer(inv -> {
            UserRole ur = inv.getArgument(0);
            ur.setId(77L);
            return ur;
        });

        RoleGrantDto result = service.grantRole(5L, new GrantRoleRequestDto("jdoe", null));

        ArgumentCaptor<UserRole> saved = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoles).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(20L);
        assertThat(saved.getValue().getRoleId()).isEqualTo(5L);
        assertThat(saved.getValue().getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(result.id()).isEqualTo(77L);
        assertThat(result.username()).isEqualTo("jdoe");
    }

    @Test
    void grantRole_userNotFound_throwsNoSuchElement() {
        when(roles.findById(5L)).thenReturn(Optional.of(role(5L, "BUYER", false)));
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grantRole(5L, new GrantRoleRequestDto("ghost", null)))
            .isInstanceOf(NoSuchElementException.class);
        verify(userRoles, never()).save(any());
    }

    @Test
    void grantRole_rejectsDuplicateGrantInSameScope() {
        Role existing = role(5L, "BUYER", false);
        AppUser user = new AppUser("jdoe", "hash", "Jane Doe", COMPANY_ID, null, ACTOR_ID);
        user.setId(20L);
        UserRole prior = new UserRole(20L, 5L, COMPANY_ID, null, ACTOR_ID);
        when(roles.findById(5L)).thenReturn(Optional.of(existing));
        when(users.findByUsername("jdoe")).thenReturn(Optional.of(user));
        when(userRoles.findByUserIdAndCompanyIdAndRevokedAtIsNull(20L, COMPANY_ID))
            .thenReturn(List.of(prior));

        assertThatThrownBy(() -> service.grantRole(5L, new GrantRoleRequestDto("jdoe", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already has role");
        verify(userRoles, never()).save(any());
    }

    // ---- revokeGrant ------------------------------------------------------

    @Test
    void revokeGrant_marksGrantRevoked() {
        UserRole grant = new UserRole(20L, 5L, COMPANY_ID, null, ACTOR_ID);
        grant.setId(77L);
        when(userRoles.findById(77L)).thenReturn(Optional.of(grant));
        when(userRoles.save(any(UserRole.class))).thenAnswer(inv -> inv.getArgument(0));

        service.revokeGrant(77L);

        assertThat(grant.getRevokedAt()).isNotNull();
        verify(userRoles).save(grant);
    }

    @Test
    void revokeGrant_notFound_throwsNoSuchElement() {
        when(userRoles.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeGrant(404L))
            .isInstanceOf(NoSuchElementException.class);
    }
}

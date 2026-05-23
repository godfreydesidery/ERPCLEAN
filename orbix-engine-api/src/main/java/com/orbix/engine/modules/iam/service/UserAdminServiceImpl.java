package com.orbix.engine.modules.iam.service;

import com.orbix.engine.modules.admin.domain.entity.Branch;
import com.orbix.engine.modules.admin.repository.BranchRepository;
import com.orbix.engine.modules.auth.repository.RefreshTokenRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.TokenGuardService;
import com.orbix.engine.modules.iam.domain.dto.ChangePasswordRequestDto;
import com.orbix.engine.modules.iam.domain.dto.CreateUserRequestDto;
import com.orbix.engine.modules.iam.domain.dto.CreateUserResponseDto;
import com.orbix.engine.modules.iam.domain.dto.ResetPasswordRequestDto;
import com.orbix.engine.modules.iam.domain.dto.ResetPasswordResponseDto;
import com.orbix.engine.modules.iam.domain.dto.RoleGrantDto;
import com.orbix.engine.modules.iam.domain.dto.UpdateUserRequestDto;
import com.orbix.engine.modules.iam.domain.dto.UserDetailDto;
import com.orbix.engine.modules.iam.domain.dto.UserSummaryDto;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.domain.entity.UserRole;
import com.orbix.engine.modules.iam.domain.enums.AppUserStatus;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import com.orbix.engine.modules.iam.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserAdminServiceImpl implements UserAdminService {

    private static final SecureRandom RNG = new SecureRandom();
    // Unambiguous alphabet for admin-issued temporary credentials —
    // no 1/I/l/0/O to avoid hand-transcription errors.
    @SuppressWarnings("java:S2068")  // not a hard-coded password — this is the source pool
    private static final String TEMP_ALPHABET =
        "abcdefghijkmnopqrstuvwxyz23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int TEMP_LENGTH = 12;

    private final AppUserRepository users;
    private final UserRoleRepository userRoles;
    private final BranchRepository branches;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final PasswordPolicyService passwordPolicy;
    private final TokenGuardService tokenGuard;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryDto> listUsers() {
        Long callerId = context.userId();
        Long companyId = context.companyId();
        // Company-wide admins see every user. Branch-scoped admins see only
        // users with a grant covering their active branch — plus orphans
        // (users with no grants yet, awaiting role assignment).
        List<AppUser> rows = userRoles.hasAnyCompanyWideGrant(callerId, companyId)
            ? users.findByDefaultCompanyIdOrderByIdAsc(companyId)
            : users.findVisibleInBranch(companyId, context.branchId());
        return rows.stream()
            .map(UserSummaryDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetailDto getUserByUid(String uid) {
        AppUser user = requireUserByUid(uid);
        return UserDetailDto.from(user, loadGrants(user));
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "AppUser")
    public CreateUserResponseDto createUser(CreateUserRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();

        String username = request.username().trim();
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        if (request.defaultBranchId() != null) {
            verifyBranchInCompany(request.defaultBranchId(), companyId);
        }

        boolean serverGenerated = request.password() == null || request.password().isBlank();
        String plain = serverGenerated ? generateTempPassword() : request.password();
        if (!serverGenerated) {
            passwordPolicy.validate(plain);
        }
        boolean mustChange = request.mustChangePassword() == null
            ? serverGenerated : request.mustChangePassword();

        AppUser user = new AppUser(
            username,
            passwords.encode(plain),
            request.displayName().trim(),
            companyId,
            request.defaultBranchId(),
            actorId
        );
        user.setEmail(emptyToNull(request.email()));
        user.setPhone(emptyToNull(request.phone()));
        user.setMustChangePassword(mustChange);

        AppUser saved = users.save(user);
        return new CreateUserResponseDto(
            UserDetailDto.from(saved, List.of()),
            serverGenerated ? plain : null
        );
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "AppUser")
    public UserDetailDto updateUserByUid(String uid, UpdateUserRequestDto request) {
        AppUser user = requireUserByUid(uid);
        if (request.defaultBranchId() != null) {
            verifyBranchInCompany(request.defaultBranchId(), user.getDefaultCompanyId());
        }
        user.updateProfile(
            request.displayName().trim(),
            emptyToNull(request.email()),
            emptyToNull(request.phone()),
            request.defaultBranchId(),
            context.userId()
        );
        return UserDetailDto.from(users.save(user), loadGrants(user));
    }

    @Override
    @Transactional
    @Auditable(action = "RESET_PASSWORD", entityType = "AppUser")
    public ResetPasswordResponseDto resetPasswordByUid(String uid, ResetPasswordRequestDto request) {
        AppUser user = requireUserByUid(uid);

        boolean serverGenerated = request.newPassword() == null || request.newPassword().isBlank();
        String plain = serverGenerated ? generateTempPassword() : request.newPassword();
        if (!serverGenerated) {
            passwordPolicy.validate(plain);
        }
        // Default to forcing the user to set their own password on next login.
        boolean mustChange = request.mustChangePassword() == null || request.mustChangePassword();

        user.resetPassword(passwords.encode(plain), mustChange, context.userId());
        AppUser saved = users.save(user);

        // Kill every active session — sever the refresh chain and invalidate
        // any still-valid access tokens immediately (the old password is gone).
        refreshTokens.revokeAllForUser(user.getId(), Instant.now());
        tokenGuard.invalidateUserTokens(user.getId());

        return new ResetPasswordResponseDto(
            UserDetailDto.from(saved, loadGrants(saved)),
            serverGenerated ? plain : null
        );
    }

    @Override
    @Transactional
    @Auditable(action = "DISABLE", entityType = "AppUser")
    public UserDetailDto disableUserByUid(String uid) {
        AppUser user = requireUserByUid(uid);
        guardSelfMutation(user, "disable yourself");
        user.setStatus(AppUserStatus.INACTIVE, context.userId());
        AppUser saved = users.save(user);
        // Lock the user out of any active session — refresh chain AND any
        // still-valid access tokens (the latter via the per-user epoch).
        refreshTokens.revokeAllForUser(user.getId(), Instant.now());
        tokenGuard.invalidateUserTokens(user.getId());
        return UserDetailDto.from(saved, loadGrants(saved));
    }

    @Override
    @Transactional
    @Auditable(action = "ENABLE", entityType = "AppUser")
    public UserDetailDto enableUserByUid(String uid) {
        AppUser user = requireUserByUid(uid);
        user.setStatus(AppUserStatus.ACTIVE, context.userId());
        return UserDetailDto.from(users.save(user), loadGrants(user));
    }

    @Override
    @Transactional
    @Auditable(action = "UNLOCK", entityType = "AppUser")
    public UserDetailDto unlockUserByUid(String uid) {
        AppUser user = requireUserByUid(uid);
        user.unlock(context.userId());
        return UserDetailDto.from(users.save(user), loadGrants(user));
    }

    @Override
    @Transactional
    @Auditable(action = "FORCE_LOGOUT", entityType = "AppUser")
    public void forceLogoutByUid(String uid) {
        AppUser user = requireUserByUid(uid);
        refreshTokens.revokeAllForUser(user.getId(), Instant.now());
        tokenGuard.invalidateUserTokens(user.getId());
    }

    @Override
    @Transactional
    @Auditable(action = "CHANGE_PASSWORD", entityType = "AppUser")
    public void changeMyPassword(ChangePasswordRequestDto request) {
        Long userId = context.userId();
        if (userId == null) throw new AccessDeniedException("Not authenticated");
        AppUser user = users.findById(userId)
            .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        if (!passwords.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AccessDeniedException("Current password is incorrect");
        }
        passwordPolicy.validate(request.newPassword());
        if (passwords.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must differ from the current one");
        }
        user.changePassword(passwords.encode(request.newPassword()), userId);
        users.save(user);

        // US-IAM-005: a password change invalidates every existing session. The
        // current client keeps its access token until it expires (≤15m), then
        // re-authenticates; all other devices are cut at their next refresh.
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private AppUser requireUserByUid(String uid) {
        Long callerId = context.userId();
        Long companyId = context.companyId();
        AppUser user = users.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("User not found: " + uid));
        if (!Objects.equals(user.getDefaultCompanyId(), companyId)) {
            throw new AccessDeniedException("User " + uid + " is not in your company");
        }
        // Branch-scoped admins can only act on users visible in their branch.
        // Company-wide admins bypass this.
        if (!userRoles.hasAnyCompanyWideGrant(callerId, companyId)
            && !userRoles.isUserVisibleInBranch(user.getId(), companyId, context.branchId())) {
            throw new AccessDeniedException(
                "User " + uid + " is not in your branch scope");
        }
        return user;
    }

    private void guardSelfMutation(AppUser target, String action) {
        if (Objects.equals(target.getId(), context.userId())) {
            throw new IllegalArgumentException("You cannot " + action);
        }
    }

    private void verifyBranchInCompany(Long branchId, Long companyId) {
        Branch branch = branches.findById(branchId)
            .orElseThrow(() -> new NoSuchElementException("Branch not found: " + branchId));
        if (!Objects.equals(branch.getCompanyId(), companyId)) {
            throw new AccessDeniedException("Branch " + branchId + " is not in your company");
        }
        // Caller holds IAM.MANAGE_USERS (a company-wide permission). We don't
        // re-run BranchAccessGuard here — the admin is provisioning the target
        // user's default branch, not switching themselves into it.
    }

    private List<RoleGrantDto> loadGrants(AppUser user) {
        List<UserRole> rows = userRoles.findByUserIdAndCompanyIdAndRevokedAtIsNull(
            user.getId(), user.getDefaultCompanyId());
        return rows.stream()
            .map(g -> RoleGrantDto.from(g, user))
            .sorted(Comparator.comparing(RoleGrantDto::grantedAt).reversed())
            .toList();
    }

    private static String emptyToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_LENGTH);
        for (int i = 0; i < TEMP_LENGTH; i++) {
            sb.append(TEMP_ALPHABET.charAt(RNG.nextInt(TEMP_ALPHABET.length())));
        }
        return sb.toString();
    }
}

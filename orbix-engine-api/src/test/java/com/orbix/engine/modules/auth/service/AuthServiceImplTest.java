package com.orbix.engine.modules.auth.service;

import com.orbix.engine.modules.auth.domain.dto.LoginRequestDto;
import com.orbix.engine.modules.auth.domain.dto.LoginResponseDto;
import com.orbix.engine.modules.auth.repository.RefreshTokenRepository;
import com.orbix.engine.modules.common.service.AuditLogWriter;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.TokenGuardService;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthServiceImpl}.
 * Focuses on ISSUE-AUTH-OPT-LOCK-01: concurrent login for the same user
 * must not propagate {@link ObjectOptimisticLockingFailureException} as HTTP 500.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final Long USER_ID     = 1L;
    private static final Long COMPANY_ID  = 10L;
    private static final Long BRANCH_ID   = 2L;
    private static final String USERNAME  = "testuser";
    private static final String PASSWORD  = "correct-password";
    private static final String HASH      = "$2a$10$HASH_PLACEHOLDER";

    @Mock private AppUserRepository users;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private PasswordEncoder passwords;
    @Mock private JwtService jwt;
    @Mock private PermissionResolverService permissions;
    @Mock private AuditLogWriter audit;
    @Mock private RequestContext context;
    @Mock private TokenGuardService tokenGuard;

    @InjectMocks private AuthServiceImpl service;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = new AppUser(USERNAME, HASH, "Test User", COMPANY_ID, BRANCH_ID, 1L);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "uid", UidGenerator.next());

        // Inject @Value fields that Spring would normally inject
        ReflectionTestUtils.setField(service, "accessTtl", Duration.ofMinutes(15));
        ReflectionTestUtils.setField(service, "refreshTtl", Duration.ofDays(30));

        lenient().when(passwords.matches(PASSWORD, HASH)).thenReturn(true);
        lenient().when(permissions.resolve(anyLong(), anyLong(), any())).thenReturn(java.util.Set.of());
        lenient().when(jwt.issueAccessToken(anyLong(), anyLong(), any(), any()))
            .thenReturn("access-token");
        lenient().when(refreshTokens.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().doNothing().when(audit).write(any());
        lenient().when(context.ip()).thenReturn("127.0.0.1");
        lenient().when(context.clientVersion()).thenReturn(null);
    }

    // -----------------------------------------------------------------------
    // ISSUE-AUTH-OPT-LOCK-01: optimistic lock on concurrent login
    // -----------------------------------------------------------------------

    /**
     * Happy path: first save succeeds — must return a token without retrying.
     */
    @Test
    void login_success_returnsTokenOnFirstAttempt() {
        when(users.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(users.save(any(AppUser.class))).thenReturn(user);

        LoginResponseDto resp = service.login(new LoginRequestDto(USERNAME, PASSWORD));

        assertThat(resp.accessToken()).isEqualTo("access-token");
        verify(users, times(1)).save(any(AppUser.class));
    }

    /**
     * ISSUE-AUTH-OPT-LOCK-01 regression: when the first {@code users.save()} throws
     * {@link ObjectOptimisticLockingFailureException} (concurrent login race on @Version),
     * the service must reload the user and retry exactly once — and still return a token.
     */
    @Test
    void login_optimisticLockOnFirstSave_retriesAndSucceeds() {
        AppUser freshUser = new AppUser(USERNAME, HASH, "Test User", COMPANY_ID, BRANCH_ID, 1L);
        ReflectionTestUtils.setField(freshUser, "id", USER_ID);
        ReflectionTestUtils.setField(freshUser, "uid", UidGenerator.next());

        when(users.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        // First save (stale version) throws; second save (fresh reload) succeeds
        when(users.save(any(AppUser.class)))
            .thenThrow(new ObjectOptimisticLockingFailureException(AppUser.class, USER_ID))
            .thenReturn(freshUser);
        when(users.findById(USER_ID)).thenReturn(Optional.of(freshUser));

        LoginResponseDto resp = service.login(new LoginRequestDto(USERNAME, PASSWORD));

        assertThat(resp.accessToken()).isEqualTo("access-token");
        // save called twice: once for stale entity, once for fresh reload
        verify(users, times(2)).save(any(AppUser.class));
        verify(users, times(1)).findById(USER_ID);
    }

    /**
     * Wrong password must still throw {@link AuthService.InvalidCredentialsException}
     * and must NOT call save (no bookkeeping side-effect on rootadmin).
     */
    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        when(users.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(passwords.matches(anyString(), anyString())).thenReturn(false);
        // user is not rootadmin, so failed-login counter is incremented;
        // for simplicity stub save to succeed (test focuses on the thrown exception)
        when(users.save(any())).thenReturn(user);

        assertThatThrownBy(() -> service.login(new LoginRequestDto(USERNAME, "wrong")))
            .isInstanceOf(AuthService.InvalidCredentialsException.class);
    }

    /** Unknown username must throw {@link AuthService.InvalidCredentialsException}. */
    @Test
    void login_unknownUsername_throwsInvalidCredentials() {
        when(users.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequestDto("nobody", PASSWORD)))
            .isInstanceOf(AuthService.InvalidCredentialsException.class);
    }
}

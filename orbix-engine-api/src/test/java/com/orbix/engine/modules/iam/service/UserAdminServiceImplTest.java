package com.orbix.engine.modules.iam.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.iam.domain.dto.UserLookupDto;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserAdminServiceImpl#lookupUsers}.
 * All other dependencies are mocked; no Spring context is loaded.
 */
@ExtendWith(MockitoExtension.class)
class UserAdminServiceImplTest {

    private static final Long COMPANY_ID = 5L;
    private static final Long ACTOR_ID   = 1L;

    // Only the collaborators exercised by lookupUsers need real mocks;
    // the rest are lenient so @InjectMocks can wire the full constructor.
    @Mock private AppUserRepository users;
    @Mock private com.orbix.engine.modules.iam.repository.UserRoleRepository userRoles;
    @Mock private com.orbix.engine.modules.admin.repository.BranchRepository branches;
    @Mock private com.orbix.engine.modules.auth.repository.RefreshTokenRepository refreshTokens;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Mock private PasswordPolicyService passwordPolicy;
    @Mock private com.orbix.engine.modules.common.service.TokenGuardService tokenGuard;
    @Mock private RootAdminGuard rootAdminGuard;
    @Mock private RequestContext context;

    @InjectMocks private UserAdminServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
    }

    // ---- helpers -----------------------------------------------------------

    private static AppUser activeUser(Long id, String displayName, String username) {
        AppUser u = new AppUser(username, "hash", displayName, COMPANY_ID, null, ACTOR_ID);
        u.setId(id);
        ReflectionTestUtils.setField(u, "uid", UidGenerator.next());
        return u;
    }

    // ---- lookupUsers -------------------------------------------------------

    @Test
    void lookupUsers_matchByName_returnsMappedDtos() {
        AppUser alice = activeUser(10L, "Alice Kamau", "akamau");
        when(users.lookupByName(eq(COMPANY_ID), eq("alice"), any(Pageable.class)))
            .thenReturn(List.of(alice));

        List<UserLookupDto> result = service.lookupUsers("alice", 10);

        assertThat(result).hasSize(1);
        UserLookupDto dto = result.get(0);
        assertThat(dto.id()).isEqualTo(10L);
        assertThat(dto.uid()).isEqualTo(alice.getUid());
        assertThat(dto.displayName()).isEqualTo("Alice Kamau");
        assertThat(dto.username()).isEqualTo("akamau");
    }

    @Test
    void lookupUsers_blankQuery_passesNullToRepository() {
        when(users.lookupByName(eq(COMPANY_ID), isNull(), any(Pageable.class)))
            .thenReturn(List.of());

        service.lookupUsers("   ", 20);

        verify(users).lookupByName(eq(COMPANY_ID), isNull(), any(Pageable.class));
    }

    @Test
    void lookupUsers_nullQuery_passesNullToRepository() {
        when(users.lookupByName(eq(COMPANY_ID), isNull(), any(Pageable.class)))
            .thenReturn(List.of());

        service.lookupUsers(null, 20);

        verify(users).lookupByName(eq(COMPANY_ID), isNull(), any(Pageable.class));
    }

    @Test
    void lookupUsers_sizeCappedAt50() {
        when(users.lookupByName(eq(COMPANY_ID), any(), any(Pageable.class)))
            .thenReturn(List.of());

        service.lookupUsers("x", 999);

        // Verify the Pageable passed to the repo has pageSize = 50 (the cap).
        verify(users).lookupByName(eq(COMPANY_ID), eq("x"),
            eq(PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "displayName"))));
    }

    @Test
    void lookupUsers_zeroPaddedToDefaultSize() {
        when(users.lookupByName(eq(COMPANY_ID), any(), any(Pageable.class)))
            .thenReturn(List.of());

        service.lookupUsers("x", 0);

        // size=0 falls back to DEFAULT_LOOKUP_SIZE=20
        verify(users).lookupByName(eq(COMPANY_ID), eq("x"),
            eq(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "displayName"))));
    }

    @Test
    void lookupUsers_scopedToCallerCompany() {
        when(users.lookupByName(eq(COMPANY_ID), any(), any(Pageable.class)))
            .thenReturn(List.of());

        service.lookupUsers("bob", 5);

        // Must never query a different company.
        verify(users).lookupByName(eq(COMPANY_ID), eq("bob"), any(Pageable.class));
    }

    @Test
    void lookupUsers_multipleResults_preservedInOrder() {
        AppUser abel  = activeUser(1L, "Abel Mwangi",  "amwangi");
        AppUser beatrice = activeUser(2L, "Beatrice Osei", "bosei");
        when(users.lookupByName(eq(COMPANY_ID), eq("a"), any(Pageable.class)))
            .thenReturn(List.of(abel, beatrice));

        List<UserLookupDto> result = service.lookupUsers("a", 10);

        assertThat(result).extracting(UserLookupDto::displayName)
            .containsExactly("Abel Mwangi", "Beatrice Osei");
    }
}

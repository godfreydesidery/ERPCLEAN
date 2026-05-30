package com.orbix.engine.modules.auth.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.TokenGuardService;
import com.orbix.engine.modules.iam.service.BranchAccessGuard;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthenticationFilter} — covers the per-user token
 * invalidation cutoff comparison (ISSUE-AUTH-01 / US-IAM-007).
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private RequestContext requestContext;
    @Mock private BranchAccessGuard branchAccessGuard;
    @Mock private TokenGuardService tokenGuard;
    @Mock private FilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, requestContext, branchAccessGuard, tokenGuard);
    }

    /**
     * ISSUE-AUTH-01 regression: when iat == invalidatedAt (same wall-clock second),
     * the old {@code <} comparison allowed the token through. The fix uses {@code <=}
     * so same-second tokens are rejected. US-IAM-007 AC: "immediately invalidates
     * active sessions on next API call".
     */
    @Test
    void filter_sameSecondIatAndInvalidatedAt_returns401() throws Exception {
        long epoch = Instant.now().getEpochSecond();
        Instant iat = Instant.ofEpochSecond(epoch);

        JwtService.Claims claims = new JwtService.Claims(
            42L, 1L, 1L, List.of(), "jti-abc", iat);

        when(jwtService.parse("token-x")).thenReturn(claims);
        when(tokenGuard.isJtiBlacklisted("jti-abc")).thenReturn(false);
        // invalidatedAt == iat epoch — same second, must REJECT
        when(tokenGuard.userInvalidatedAtEpoch(42L)).thenReturn(epoch);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer token-x");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        // Chain must NOT be called — request is rejected before dispatch
        verify(chain, never()).doFilter(any(), any());
    }

    /** Control case: iat strictly before invalidatedAt — must also reject. */
    @Test
    void filter_iatBeforeInvalidatedAt_returns401() throws Exception {
        long epoch = Instant.now().getEpochSecond();
        Instant iat = Instant.ofEpochSecond(epoch - 5); // issued 5 seconds ago

        JwtService.Claims claims = new JwtService.Claims(
            42L, 1L, 1L, List.of(), "jti-old", iat);

        when(jwtService.parse("token-old")).thenReturn(claims);
        when(tokenGuard.isJtiBlacklisted("jti-old")).thenReturn(false);
        when(tokenGuard.userInvalidatedAtEpoch(42L)).thenReturn(epoch); // invalidated now

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer token-old");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    /** Token issued AFTER the invalidation cutoff must pass through. */
    @Test
    void filter_iatAfterInvalidatedAt_allowsThrough() throws Exception {
        long epoch = Instant.now().getEpochSecond();
        Instant iat = Instant.ofEpochSecond(epoch + 2); // issued 2 seconds after invalidation

        JwtService.Claims claims = new JwtService.Claims(
            42L, 1L, 1L, List.of(), "jti-new", iat);

        when(jwtService.parse("token-new")).thenReturn(claims);
        when(tokenGuard.isJtiBlacklisted("jti-new")).thenReturn(false);
        when(tokenGuard.userInvalidatedAtEpoch(42L)).thenReturn(epoch); // invalidated before iat

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer token-new");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        // Should reach the chain (authentication succeeds, SecurityContext populated)
        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isNotEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    /** No invalidation record (epoch == 0) — token should always pass the cutoff check. */
    @Test
    void filter_noInvalidationRecord_allowsThrough() throws Exception {
        Instant iat = Instant.now();
        JwtService.Claims claims = new JwtService.Claims(
            99L, 1L, 1L, List.of(), "jti-free", iat);

        when(jwtService.parse("token-free")).thenReturn(claims);
        when(tokenGuard.isJtiBlacklisted("jti-free")).thenReturn(false);
        when(tokenGuard.userInvalidatedAtEpoch(99L)).thenReturn(0L); // no record

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer token-free");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
    }
}

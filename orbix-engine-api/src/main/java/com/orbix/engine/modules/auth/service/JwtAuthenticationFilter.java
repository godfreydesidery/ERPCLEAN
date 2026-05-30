package com.orbix.engine.modules.auth.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.TokenGuardService;
import com.orbix.engine.modules.iam.service.BranchAccessGuard;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates the access JWT on every request, populates the SecurityContext,
 * and binds the request's company / branch via {@link RequestContext}.
 *
 * <p>When a request supplies an {@code X-Branch-Id} that differs from the
 * token's branch, {@link BranchAccessGuard} confirms the user actually has a
 * role grant for that branch before the override is applied.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RequestContext requestContext;
    private final BranchAccessGuard branchAccessGuard;
    private final TokenGuardService tokenGuard;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   RequestContext requestContext,
                                   BranchAccessGuard branchAccessGuard,
                                   TokenGuardService tokenGuard) {
        this.jwtService = jwtService;
        this.requestContext = requestContext;
        this.branchAccessGuard = branchAccessGuard;
        this.tokenGuard = tokenGuard;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String clientVersion = request.getHeader("X-Client-Version");
        String ip = clientIp(request);
        // Always capture transport metadata — anonymous requests (e.g. login)
        // need it for audit even though they carry no identity yet.
        requestContext.bindClient(clientVersion, ip);

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            JwtService.Claims claims;
            try {
                claims = jwtService.parse(token);
            } catch (Exception ex) {
                deny(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }

            // Immediate revocation (US-IAM-002/007): a blacklisted jti, or a token
            // issued before this user's invalidation cutoff, is rejected even
            // though its signature and expiry are still valid.
            if (tokenGuard.isJtiBlacklisted(claims.jti())) {
                deny(response, HttpServletResponse.SC_UNAUTHORIZED, "Token revoked");
                return;
            }
            long invalidatedAt = tokenGuard.userInvalidatedAtEpoch(claims.userId());
            // Use <= so a token issued in the same wall-clock second as the invalidation
            // is also rejected (US-IAM-007: "immediately invalidates active sessions").
            // Both iat and invalidatedAt are truncated to whole seconds, so strict < allows
            // a token issued at second T to survive an invalidation also written at T.
            if (invalidatedAt > 0 && claims.issuedAt() != null
                    && claims.issuedAt().getEpochSecond() <= invalidatedAt) {
                deny(response, HttpServletResponse.SC_UNAUTHORIZED, "Session revoked");
                return;
            }

            Long requestedBranch = parseLong(request.getHeader("X-Branch-Id"));
            if (requestedBranch != null && !requestedBranch.equals(claims.branchId())) {
                try {
                    branchAccessGuard.verify(claims.userId(), claims.companyId(), requestedBranch);
                } catch (AccessDeniedException ex) {
                    deny(response, HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
                    return;
                }
            }

            List<SimpleGrantedAuthority> authorities = claims.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(claims.userId(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);

            requestContext.bind(
                claims.userId(),
                claims.companyId(),
                requestedBranch != null ? requestedBranch : claims.branchId(),
                clientVersion,
                ip,
                claims.jti()
            );
        }
        try {
            chain.doFilter(request, response);
        } finally {
            requestContext.clear();
        }
    }

    /**
     * Reject by writing the status directly. We do NOT use
     * {@code response.sendError()} here: that triggers a container ERROR
     * dispatch to {@code /error}, which re-enters the security chain as an
     * anonymous user and is denied 403 — masking our intended 401 and breaking
     * the web client's 401-driven token-refresh.
     */
    private static void deny(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message.replace("\"", "'") + "\"}");
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Long.parseLong(value); } catch (NumberFormatException e) { return null; }
    }

    /** Best-effort client IP: first hop of X-Forwarded-For, else the socket address. */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

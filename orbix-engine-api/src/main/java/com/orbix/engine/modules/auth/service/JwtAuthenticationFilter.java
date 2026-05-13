package com.orbix.engine.modules.auth.service;

import com.orbix.engine.modules.common.service.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RequestContext requestContext;

    public JwtAuthenticationFilter(JwtService jwtService, RequestContext requestContext) {
        this.jwtService = jwtService;
        this.requestContext = requestContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                JwtService.Claims claims = jwtService.parse(token);
                List<SimpleGrantedAuthority> authorities = claims.privileges().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(claims.userId(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);

                Long requestedBranch = parseLong(request.getHeader("X-Branch-Id"));
                requestContext.bind(
                    claims.userId(),
                    claims.companyId(),
                    requestedBranch != null ? requestedBranch : claims.branchId(),
                    request.getHeader("X-Client-Version")
                );
            } catch (Exception ex) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            requestContext.clear();
        }
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Long.parseLong(value); } catch (NumberFormatException e) { return null; }
    }
}

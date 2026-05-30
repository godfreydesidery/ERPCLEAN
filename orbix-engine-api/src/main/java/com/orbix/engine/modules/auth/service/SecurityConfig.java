package com.orbix.engine.modules.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbix.engine.modules.common.domain.dto.ApiResponseDto;
import com.orbix.engine.modules.common.domain.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Replaces the legacy {@code SecurityConfig} that used permissive CORS,
 * a hard-coded JWT secret, and {@code .anyRequest().permitAll()}.
 * <p>
 * Default stance: everything authenticated. Public endpoints are listed explicitly.
 * Per-action authorisation lives on the methods themselves via @PreAuthorize.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost 12 per ARCHITECTURE.md §7.1; tunable per deployment.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           ObjectMapper objectMapper) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                // ISSUE-AUTH-02: unauthenticated request to a protected endpoint →
                // HTTP 401 in the ApiResponse envelope (not a raw 403 /error body).
                // Note: the JwtAuthenticationFilter already handles expired/invalid
                // token 401 by writing directly to the response (see deny()). This
                // entry point fires for requests that carry NO token at all and reach
                // ExceptionTranslationFilter before any controller advice can run.
                .authenticationEntryPoint(restAuthEntryPoint(objectMapper))
                .accessDeniedHandler(restAccessDeniedHandler(objectMapper))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/**",
                    "/api/v1/setup/**",
                    "/actuator/health",
                    "/actuator/info",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                // The API surface stays locked (each endpoint also @PreAuthorize-gated);
                // actuator beyond health/info too.
                .requestMatchers("/api/**", "/actuator/**").authenticated()
                // Everything else is the static Angular bundle + SPA deep links
                // (served from classpath:/static/ by SpaForwardConfig when the QA
                // image bakes dist/ into the jar) — public, like any web app's
                // HTML/JS/CSS. In dev /static is empty, so this is a no-op there.
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Explicit allow-list per environment — never "*" in production.
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "https://*.orbix.local"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Total-Count", "X-Client-Version"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Writes a 401 ApiResponse envelope when a request has no (or an invalid)
     * credentials and reaches a protected endpoint. Keeps the SPA's silent
     * token-refresh working: the Angular HTTP interceptor watches for 401.
     */
    private AuthenticationEntryPoint restAuthEntryPoint(ObjectMapper mapper) {
        return (HttpServletRequest request, HttpServletResponse response,
                AuthenticationException ex) -> {
            writeEnvelope(response, HttpServletResponse.SC_UNAUTHORIZED,
                ResponseCode.UNAUTHORIZED, "Not authenticated", mapper);
        };
    }

    /**
     * Writes a 403 ApiResponse envelope when an authenticated user lacks the
     * required permission. Keeps the response contract consistent with every
     * other 4xx from the API layer.
     */
    private AccessDeniedHandler restAccessDeniedHandler(ObjectMapper mapper) {
        return (HttpServletRequest request, HttpServletResponse response,
                AccessDeniedException ex) -> {
            writeEnvelope(response, HttpServletResponse.SC_FORBIDDEN,
                ResponseCode.FORBIDDEN, "Access denied", mapper);
        };
    }

    private static void writeEnvelope(HttpServletResponse response, int status,
                                      String responseCode, String message,
                                      ObjectMapper mapper) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponseDto<Object> body = ApiResponseDto.error(status, responseCode, message);
        mapper.writeValue(response.getWriter(), body);
    }
}

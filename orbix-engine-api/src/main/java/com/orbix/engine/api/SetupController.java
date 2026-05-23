package com.orbix.engine.api;

import com.orbix.engine.modules.admin.service.BootstrapProperties;
import com.orbix.engine.modules.admin.service.FirstRunSetupService;
import com.orbix.engine.modules.iam.domain.RootAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * First-run status + the token-gated rootadmin password reset.
 *
 * <p>Bootstrapping itself is env-driven ({@code BootstrapRunner}) — there is no
 * interactive first-run endpoint. {@code GET /status} is public so the web shell
 * can choose login-vs-setup.
 *
 * <p>{@code POST /reset-rootadmin-password} is reachable without a login but
 * requires the {@code X-Bootstrap-Token} shared secret. It takes <b>no</b>
 * password in the body — it only re-applies the server-side env password
 * ({@code orbix.bootstrap.admin.password}). So a caller can re-sync the root
 * credential but can never set an attacker-chosen one.
 */
@RestController
@RequestMapping("/api/v1/setup")
@RequiredArgsConstructor
public class SetupController {

    private static final String RESET = "reset";

    private final FirstRunSetupService firstRunSetupService;
    private final BootstrapProperties bootstrap;

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("bootstrapped", firstRunSetupService.isBootstrapped());
    }

    @PostMapping("/reset-rootadmin-password")
    public ResponseEntity<Map<String, Boolean>> resetRootAdminPassword(
            @RequestHeader(value = "X-Bootstrap-Token", required = false) String token) {
        String expected = bootstrap.resetToken();
        String envPassword = bootstrap.admin().password();
        // No token configured, or no env password to apply -> endpoint disabled.
        if (expected == null || expected.isBlank() || envPassword == null || envPassword.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(RESET, false));
        }
        if (!constantTimeEquals(expected, token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(RESET, false));
        }
        if (!firstRunSetupService.isBootstrapped()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(RESET, false));
        }
        firstRunSetupService.resetAdminPassword(RootAdmin.USERNAME, envPassword);
        return ResponseEntity.ok(Map.of(RESET, true));
    }

    /** Length-leak-resistant comparison of the bootstrap token. */
    private static boolean constantTimeEquals(String expected, String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            provided.getBytes(StandardCharsets.UTF_8));
    }
}

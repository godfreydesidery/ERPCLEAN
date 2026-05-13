package com.orbix.engine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: backend starts and answers /api/v1/ping.
 * Uses an in-memory H2 substitute via Testcontainers in real builds.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthSmokeTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    @Test
    void ping_returns_ok() {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = rest.getForObject(
            "http://localhost:" + port + "/api/v1/ping", Map.class
        );
        assertThat(body).containsEntry("ok", true);
        assertThat(body).containsEntry("service", "orbix-engine-api");
    }
}

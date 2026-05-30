package com.orbix.engine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Full-stack boot smoke test — the CI gate for V79-class regressions.
 *
 * <p>Starts a real MariaDB 11 container via Testcontainers, runs ALL Flyway
 * migrations (common + mysql dirs) against it, then boots the complete Spring
 * ApplicationContext with {@code ddl-auto=validate}. Any failure of the type
 * that broke V79 (duplicate permission id, missing version column,
 * schema/entity mismatch) will surface here before reaching QA or production.
 *
 * <p>When Docker is absent (dockerless CI, sandboxed env) both test methods
 * skip gracefully via {@code assumeTrue} — the normal unit-test suite
 * continues to pass. When Docker IS present the test runs and gates boot.
 *
 * <p>The {@code smoketest} profile supplies the MariaDB dialect, Flyway
 * locations, disabled env-driven bootstrap, and a disabled Redis health
 * indicator (Redis is not required for the boot gate; its failure is tolerated
 * at runtime by {@link com.orbix.engine.modules.common.service.TokenGuardServiceImpl}).
 */
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("smoketest")
class BootSmokeIT {

    private static final String SKIP_MSG =
            "Docker is not available — skipping boot smoke test";

    /**
     * MariaDB 11 container declared as a compatible substitute for the MySQL
     * image so {@link MySQLContainer} accepts it. The mysql-connector-j driver
     * works against MariaDB via the MySQL wire protocol, matching the production
     * mysql profile exactly.
     */
    @Container
    @SuppressWarnings("resource")
    static final MySQLContainer<?> MARIADB = new MySQLContainer<>(
            DockerImageName.parse("mariadb:11").asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("orbix_smoke")
            .withUsername("smoke")
            .withPassword("smoke")
            // MariaDB 10.3+ supports CREATE SEQUENCE natively; our mysql/ Flyway
            // dir uses this. No extra flags needed for MariaDB 11.
            .withCommand("--character-set-server=utf8mb4",
                         "--collation-server=utf8mb4_unicode_ci",
                         "--explicit-defaults-for-timestamp=1");

    /**
     * Wire the container's JDBC URL into Spring before the ApplicationContext
     * is created. This overrides the placeholder datasource in
     * {@code application-smoketest.yml}.
     */
    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        // Skip property registration when Docker is absent; each test method
        // guards itself with assumeTrue before Spring uses anything.
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            return;
        }
        registry.add("spring.datasource.url",               MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username",          MARIADB::getUsername);
        registry.add("spring.datasource.password",          MARIADB::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    /**
     * Primary gate: the ApplicationContext started, Flyway ran every migration
     * without error, and Hibernate validated the schema against all JPA entities.
     * The actuator health endpoint is publicly accessible (see SecurityConfig
     * permitAll list) and returns UP when the DB connection pool is healthy.
     *
     * <p>A V79-class failure (duplicate permission id, missing column) would
     * crash context startup and turn this test into an ERROR before this
     * assertion is ever reached.
     */
    @Test
    void contextBootsAndActuatorHealthIsUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), SKIP_MSG);

        @SuppressWarnings("unchecked")
        Map<String, Object> health = rest.getForObject(
                "http://localhost:" + port + "/actuator/health", Map.class);

        assertThat(health)
                .as("actuator /health must return {status: UP} — "
                        + "DOWN means DB/Flyway/Hibernate validation failed")
                .containsEntry("status", "UP");
    }
}

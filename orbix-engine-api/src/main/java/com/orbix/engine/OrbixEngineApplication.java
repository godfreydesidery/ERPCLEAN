package com.orbix.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 * Package structure under this package:
 *   api.*         REST controllers (one folder for the whole HTTP surface)
 *   modules.*     all logic, grouped by concern:
 *                   auth          identity, login, JWT, security filter
 *                   common        cross-cutting infrastructure (audit
 *                                 aspect, transactional outbox, RequestContext)
 *                   business modules — minimum supermarket set:
 *                     party, catalog, stock, procurement, sales,
 *                     pos, cash, day
 *                 every module follows the same shape:
 *                   domain.entity   JPA entities
 *                   domain.dto      request/response payloads
 *                   domain.enums    enumeration types
 *                   domain.event    domain events (outbox payloads)
 *                   service         transactional services
 *                   repository      Spring Data JPA repositories
 * Module boundaries are enforced by ArchUnit tests
 * (see test package com.orbix.engine.architecture).
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class OrbixEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrbixEngineApplication.class, args);
    }
}

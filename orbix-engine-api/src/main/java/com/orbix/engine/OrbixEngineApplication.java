package com.orbix.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 * Package structure under this package:
 *   platform.*    cross-cutting (security, audit, events, health, company)
 *   api.*         REST controllers for every module + cross-cutting endpoints
 *   modules.*     business modules — minimum set to run a supermarket:
 *                 party, catalog, stock, procurement, sales, pos, cash, day
 *                 each with sub-packages:
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

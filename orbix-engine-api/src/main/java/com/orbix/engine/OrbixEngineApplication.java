package com.orbix.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 * Module structure under this package:
 *   platform.*    cross-cutting (security, audit, events, health)
 *   api.*         cross-cutting API surface
 *   modules.*     business modules — minimum set to run a supermarket:
 *                 party, catalog, stock, procurement, sales, pos, cash, day
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

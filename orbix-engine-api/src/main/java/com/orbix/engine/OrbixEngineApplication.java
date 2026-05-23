package com.orbix.engine;

import com.orbix.engine.modules.admin.service.BootstrapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 * Package structure under this package:
 *   api.*         REST controllers (one folder for the whole HTTP surface)
 *   modules.*     all logic, grouped by concern:
 *                   auth          authentication + authorization: login,
 *                                 refresh, JWT, security filter chain
 *                   iam           identity + access management: users,
 *                                 roles, permissions, dev seed
 *                   common        cross-cutting infrastructure (audit
 *                                 aspect, transactional outbox, RequestContext)
 *                   admin         org, branch, section, currency, fx_rate masters
 *                   party         customer / supplier / employee / agent
 *                   catalog       item master, prices, promotions
 *                   stock         ledger, balances, batches, transfers
 *                   procurement   quotation → LPO → GRN → supplier payment
 *                   sales         back-office quotation → invoice → receipt
 *                   pos           till operations, sessions, refunds, FX tender
 *                   cash          cash book, supplier payment, banking
 *                   day           business-day open / close / override
 *                   production    BOM, sub-recipes, batches, wastage
 *                   orders        layby + pre-orders
 *                   giftcard      gift card lifecycle + redemption ledger
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
@EnableConfigurationProperties(BootstrapProperties.class)
public class OrbixEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrbixEngineApplication.class, args);
    }
}

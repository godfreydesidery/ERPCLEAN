package com.orbix.engine.modules.fiscal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Selects the active FiscalProvider based on {@code orbix.fiscal.regime}.
 *
 * <p>All Spring-managed FiscalProvider beans are injected as a list. The factory
 * picks the one whose {@link FiscalProvider#regimeCode()} matches the configured
 * regime. If no match is found, it falls back to {@link NoOpFiscalProvider} and
 * logs a warning — safe for non-TZ deployments where the regime may be absent.
 *
 * <p>Because providers are Spring conditional (@ConditionalOnProperty), only the
 * appropriate bean(s) are in the context. In practice:
 * <ul>
 *   <li>regime=NONE  → only NoOpFiscalProvider is in context</li>
 *   <li>regime=TZ_VFD → NoOpFiscalProvider + TraVfdFiscalProvider both present;
 *       TraVfdFiscalProvider is selected by regimeCode matching</li>
 * </ul>
 */
@Component
public class FiscalProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(FiscalProviderFactory.class);

    private final List<FiscalProvider> providers;
    private final String regime;
    private final NoOpFiscalProvider noOp;

    public FiscalProviderFactory(List<FiscalProvider> providers,
                                 @Value("${orbix.fiscal.regime:NONE}") String regime,
                                 NoOpFiscalProvider noOp) {
        this.providers = providers;
        this.regime = regime;
        this.noOp = noOp;
        log.info("FiscalProviderFactory initialized with regime={}, available providers: {}",
            regime, providers.stream().map(FiscalProvider::regimeCode).toList());
    }

    /** Returns the active provider for the configured regime. */
    public FiscalProvider getProvider() {
        return providers.stream()
            .filter(p -> p.regimeCode().equalsIgnoreCase(regime))
            .findFirst()
            .orElseGet(() -> {
                log.warn("No FiscalProvider found for regime='{}'; falling back to NoOp", regime);
                return noOp;
            });
    }
}

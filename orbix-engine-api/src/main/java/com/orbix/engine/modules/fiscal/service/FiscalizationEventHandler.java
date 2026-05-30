package com.orbix.engine.modules.fiscal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbix.engine.modules.common.service.DomainEventHandler;
import com.orbix.engine.modules.fiscal.domain.event.FiscalizationRequestedEventDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Outbox event handler for "FiscalizationRequested.v1" domain events.
 *
 * <p>Implements {@link DomainEventHandler} so that {@link com.orbix.engine.modules.common.service.OutboxDispatcher}
 * can route the event to this handler without importing any fiscal class directly.
 * The dispatcher routes by the {@link #eventType()} string — the only coupling
 * from common to fiscal is through this interface contract.
 *
 * <p>Idempotent: delegates to FiscalizationService which skips already-terminal receipts.
 * Re-throws on failure so the OutboxDispatcher records the attempt and retries.
 */
@Component
@RequiredArgsConstructor
public class FiscalizationEventHandler implements DomainEventHandler {

    private static final Logger log = LoggerFactory.getLogger(FiscalizationEventHandler.class);

    /** The versioned event type string emitted by PosSaleService.post. */
    public static final String EVENT_TYPE = "FiscalizationRequested.v1";

    private final FiscalizationService fiscalizationService;
    private final ObjectMapper objectMapper;

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    /**
     * Handle a FiscalizationRequested.v1 event payload.
     *
     * @param payloadJson the domain_event.payload_json value
     * @throws Exception on parse or fiscalization failure (triggers outbox retry)
     */
    @Override
    public void handle(String payloadJson) throws Exception {
        FiscalizationRequestedEventDto event =
            objectMapper.readValue(payloadJson, FiscalizationRequestedEventDto.class);

        log.info("FiscalizationEventHandler: handling event for posSaleId={} sale={}",
            event.posSaleId(), event.saleNumber());

        fiscalizationService.handleFiscalizationRequested(
            event.posSaleId(),
            event.companyId(),
            event.branchId(),
            event.actorId()
        );
    }
}

package com.orbix.engine.modules.fiscal.domain.event;

/**
 * Payload of the "FiscalizationRequested.v1" domain event emitted by
 * PosSaleService.post into the transactional outbox.
 *
 * <p>This DTO is the only coupling point from the pos module to the fiscal
 * module: pos serializes this to JSON in the domain_event.payload_json column;
 * fiscal deserializes it in FiscalizationEventHandler. pos does NOT import
 * any fiscal class — the coupling is purely through the event type string and
 * this published DTO in the fiscal module's domain.event package.
 *
 * <p>Keep the field set minimal: fiscal can load any additional data it needs
 * from the pos_sale and pos_sale_line tables using the posSaleId.
 */
public record FiscalizationRequestedEventDto(

    /** POS sale id — fiscal uses this to load the full sale for the provider. */
    Long posSaleId,

    /** POS sale business number (for logging/correlation). */
    String saleNumber,

    Long companyId,
    Long branchId,
    Long actorId

) {}

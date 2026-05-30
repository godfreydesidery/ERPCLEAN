package com.orbix.engine.modules.fiscal.service;

import com.orbix.engine.modules.fiscal.domain.dto.FiscalReceiptDto;

import java.util.Optional;

/**
 * Application service for the FiscalReceipt aggregate.
 *
 * <p>Primary entry point for:
 * <ul>
 *   <li>Handling a FiscalizationRequested.v1 outbox event (consume → fiscalize → persist).</li>
 *   <li>Reading fiscal receipt status for a given POS sale (for the sync-pull payload).</li>
 * </ul>
 */
public interface FiscalizationService {

    /**
     * Handle a FiscalizationRequested event from the outbox. Loads the POS sale,
     * calls the active FiscalProvider, persists the FiscalReceipt result, and
     * mirrors the fiscal status fields back onto pos_sale.
     *
     * <p>Called by the FiscalizationEventHandler (the outbox consumer). Must be
     * idempotent: if a FiscalReceipt row already exists for this posSaleId and
     * is already FISCALIZED, the call is a no-op.
     *
     * @param posSaleId POS sale id from the event payload
     * @param companyId company context
     * @param branchId  branch context
     * @param actorId   actor from the original sale transaction
     */
    void handleFiscalizationRequested(Long posSaleId, Long companyId, Long branchId, Long actorId);

    /**
     * Return the fiscal receipt for a given POS sale id, if one exists.
     *
     * @param posSaleId the POS sale id (internal Long)
     * @return the fiscal receipt DTO, or empty if no fiscalization was attempted
     */
    Optional<FiscalReceiptDto> findByPosSaleId(Long posSaleId);

    /**
     * Return the fiscal receipt by its uid.
     *
     * @param uid the ULID uid
     * @return the fiscal receipt DTO
     * @throws java.util.NoSuchElementException if not found
     */
    FiscalReceiptDto getByUid(String uid);
}

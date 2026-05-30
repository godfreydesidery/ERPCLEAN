package com.orbix.engine.modules.fiscal.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.fiscal.domain.dto.FiscalizableSaleDto;
import com.orbix.engine.modules.fiscal.domain.dto.FiscalReceiptDto;
import com.orbix.engine.modules.fiscal.domain.dto.FiscalReceiptResultDto;
import com.orbix.engine.modules.fiscal.domain.entity.FiscalReceipt;
import com.orbix.engine.modules.fiscal.domain.enums.FiscalStatus;
import com.orbix.engine.modules.fiscal.repository.FiscalReceiptRepository;
import com.orbix.engine.modules.pos.domain.entity.PosSale;
import com.orbix.engine.modules.pos.domain.entity.PosSaleLine;
import com.orbix.engine.modules.pos.repository.PosSaleLineRepository;
import com.orbix.engine.modules.pos.repository.PosSaleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Handles fiscalization requests from the outbox and manages the FiscalReceipt lifecycle.
 *
 * <p>Boundary rules (ADR-0006 / ModuleBoundaryTest):
 * <ul>
 *   <li>fiscal reads pos data via pos repositories (allowed by ArchUnit ADR-0004 latent-gap carve-out).</li>
 *   <li>fiscal reads catalog data via catalog repositories (for item code/description).</li>
 *   <li>fiscal does NOT call any pos service method — only reads from repos.</li>
 *   <li>pos does NOT import any fiscal class — it only emits the outbox event.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FiscalizationServiceImpl implements FiscalizationService {

    private static final Logger log = LoggerFactory.getLogger(FiscalizationServiceImpl.class);

    private final FiscalReceiptRepository fiscalReceipts;
    private final PosSaleRepository posSales;
    private final PosSaleLineRepository posSaleLines;
    private final ItemRepository items;
    private final FiscalProviderFactory providerFactory;

    /**
     * Seller TIN for TZ-VFD receipts. STUB: field name/format pending TRA EFDMS spec confirmation.
     * Loaded from orbix.fiscal.tra.tin.
     */
    @Value("${orbix.fiscal.tra.tin:}")
    private String sellerTin;

    /**
     * Seller VRN. STUB: pending TRA EFDMS spec confirmation.
     * Loaded from orbix.fiscal.tra.vrn.
     */
    @Value("${orbix.fiscal.tra.vrn:}")
    private String sellerVrn;

    @Override
    @Transactional
    public void handleFiscalizationRequested(Long posSaleId, Long companyId, Long branchId,
                                             Long actorId) {
        // Idempotency: if already FISCALIZED, skip.
        Optional<FiscalReceipt> existingOpt = fiscalReceipts.findByPosSaleId(posSaleId);
        if (existingOpt.isPresent()) {
            FiscalReceipt existing = existingOpt.get();
            if (existing.getStatus() == FiscalStatus.FISCALIZED
                || existing.getStatus() == FiscalStatus.NONE
                || existing.getStatus() == FiscalStatus.EXEMPT) {
                log.debug("FiscalizationService: sale {} already in terminal state {}, skipping",
                    posSaleId, existing.getStatus());
                return;
            }
        }

        FiscalProvider provider = providerFactory.getProvider();

        // Create or reuse the FiscalReceipt row.
        FiscalReceipt receipt = existingOpt.orElseGet(() -> {
            FiscalReceipt r = new FiscalReceipt(posSaleId, companyId, branchId,
                provider.regimeCode(), actorId);
            return fiscalReceipts.save(r);
        });

        // Mark sale as PROVISIONAL so the POS can print a provisional receipt
        // while the async EFDMS call completes.
        stampPosSale(posSaleId, FiscalStatus.PROVISIONAL, null, null, null);

        FiscalReceiptResultDto result;
        try {
            FiscalizableSaleDto saleDto = buildFiscalizableDto(posSaleId, companyId);
            result = provider.fiscalize(saleDto);
        } catch (Exception ex) {
            log.warn("FiscalizationService: EFDMS call failed for posSaleId={}: {}",
                posSaleId, ex.getMessage());
            receipt.recordFailure(ex.getMessage(), actorId);
            stampPosSale(posSaleId, FiscalStatus.FAILED, null, null, null);
            // Re-throw so the outbox poller records the failure and retries.
            throw ex;
        }

        if (result.status() == FiscalStatus.NONE) {
            receipt.markNone(actorId);
            stampPosSale(posSaleId, FiscalStatus.NONE, null, null, null);
        } else if (result.status() == FiscalStatus.FISCALIZED) {
            receipt.applyResult(
                FiscalStatus.FISCALIZED,
                result.rctnum(), result.gc(), result.dc(), result.znum(),
                result.verificationCode(), result.verifyUrl(), result.qrPayload(),
                result.signature(), result.rawResponse(), actorId
            );
            stampPosSale(posSaleId, FiscalStatus.FISCALIZED,
                result.verificationCode(), result.qrPayload(), result.signature());
        } else {
            receipt.recordFailure(result.errorMessage(), actorId);
            stampPosSale(posSaleId, FiscalStatus.FAILED, null, null, null);
        }

        log.info("FiscalizationService: posSaleId={} → status={} rctnum={}",
            posSaleId, receipt.getStatus(), receipt.getRctnum());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FiscalReceiptDto> findByPosSaleId(Long posSaleId) {
        return fiscalReceipts.findByPosSaleId(posSaleId).map(FiscalReceiptDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public FiscalReceiptDto getByUid(String uid) {
        return fiscalReceipts.findByUid(uid)
            .map(FiscalReceiptDto::from)
            .orElseThrow(() -> new NoSuchElementException("FiscalReceipt not found: " + uid));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Build the fiscalizable sale DTO from the pos_sale + pos_sale_line rows.
     * This is the cross-module read: fiscal reads pos data directly from the
     * pos repository (ADR-0004 latent-gap carve-out in ModuleBoundaryTest).
     */
    private FiscalizableSaleDto buildFiscalizableDto(Long posSaleId, Long companyId) {
        PosSale sale = posSales.findById(posSaleId)
            .orElseThrow(() -> new NoSuchElementException("PosSale not found: " + posSaleId));
        List<PosSaleLine> saleLines = posSaleLines.findByPosSaleIdOrderByLineNoAsc(posSaleId);

        List<FiscalizableSaleDto.LineDto> lineDtos = new ArrayList<>(saleLines.size());
        for (PosSaleLine l : saleLines) {
            Item item = items.findById(l.getItemId()).orElse(null);
            String itemCode = item != null ? item.getCode() : "UNKNOWN";
            String itemDesc = item != null ? item.getName() : "Unknown item";

            BigDecimal netAmount = l.getLineTotal().subtract(l.getTaxAmount());

            // STUB: taxCode derivation from VAT rate is a placeholder.
            // TRA uses specific codes (A=standard, B=special, C=zero, D=exempt).
            // Replace with real VatGroup.traTaxCode() lookup once spec is confirmed.
            String taxCode = deriveTaxCodeStub(l.getTaxAmount(), l.getLineTotal()); // STUB: pending TRA EFDMS spec confirmation

            lineDtos.add(new FiscalizableSaleDto.LineDto(
                l.getLineNo(), itemCode, itemDesc,
                l.getQty(), l.getUnitPrice(), l.getDiscountAmount(),
                netAmount, taxCode, l.getTaxAmount(), l.getLineTotal()
            ));
        }

        BigDecimal totalInclTax = sale.getTotalAmount();
        BigDecimal totalTax = sale.getTaxAmount();
        BigDecimal totalExclTax = totalInclTax.subtract(totalTax);

        return new FiscalizableSaleDto(
            sale.getId(), sale.getNumber(), sale.getSaleAt(),
            sale.getCompanyId(), sale.getBranchId(),
            sellerTin, sellerVrn,
            null, null,  // buyerTin, buyerVrn — optional walk-in; STUB: rules pending
            lineDtos,
            totalExclTax, totalTax, totalInclTax,
            "TZS"  // STUB: should come from company currency; hardcoded TZS for TZ pilot
        );
    }

    /**
     * STUB: derive a TRA VAT tax code from the tax/total ratio.
     * Real implementation needs a mapping from VatGroup to TRA tax code.
     * STUB: pending TRA EFDMS spec confirmation — replace with VatGroup.traTaxCode().
     */
    private String deriveTaxCodeStub(BigDecimal taxAmount, BigDecimal lineTotal) { // STUB: pending TRA EFDMS spec confirmation
        if (taxAmount == null || lineTotal == null || lineTotal.signum() == 0) {
            return "D"; // STUB: exempt; pending TRA EFDMS spec confirmation
        }
        if (taxAmount.signum() == 0) {
            return "C"; // STUB: zero-rated; pending TRA EFDMS spec confirmation
        }
        return "A"; // STUB: standard rate; pending TRA EFDMS spec confirmation
    }

    /**
     * Mirror fiscal status + artefacts onto the pos_sale row so the sync-pull
     * and reprint paths can read them without crossing module boundaries.
     */
    private void stampPosSale(Long posSaleId, FiscalStatus status,
                              String verificationCode, String qrPayload, String signature) {
        posSales.findById(posSaleId).ifPresent(sale -> {
            sale.setFiscalStatus(status != null ? status.name() : null);
            sale.setFiscalVerificationCode(verificationCode);
            sale.setFiscalQrPayload(qrPayload);
            if (signature != null) {
                sale.setFiscalSignature(signature);
            }
        });
    }
}

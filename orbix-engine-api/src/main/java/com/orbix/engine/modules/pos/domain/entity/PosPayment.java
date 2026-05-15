package com.orbix.engine.modules.pos.domain.entity;

import com.orbix.engine.modules.pos.domain.enums.PosPaymentMethod;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** POS-sale tender row — supports mixed tender per sale. DATA-MODEL.md §7.5. */
@Entity
@Table(name = "pos_payment")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class PosPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pos_payment_seq")
    @SequenceGenerator(name = "pos_payment_seq", sequenceName = "pos_payment_seq", allocationSize = 50)
    private Long id;

    @Column(name = "pos_sale_id", nullable = false)
    private Long posSaleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PosPaymentMethod method;

    /** Functional-currency-converted amount: {@code tenderAmount * fxRateSnapshot}. */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "tender_currency", nullable = false, length = 3)
    private String tenderCurrency;

    @Column(name = "tender_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal tenderAmount;

    /** Snapshot of {@code tenderCurrency → company functional currency} at sale time. */
    @Column(name = "fx_rate_snapshot", nullable = false, precision = 20, scale = 8)
    private BigDecimal fxRateSnapshot;

    @Column(length = 80)
    private String reference;

    @Column(name = "terminal_id", length = 40)
    private String terminalId;

    @Column(length = 4)
    private String last4;

    @SuppressWarnings("java:S107")  // FX-aware tender row needs the snapshot bundle
    public PosPayment(Long posSaleId, PosPaymentMethod method, BigDecimal amount,
                      String tenderCurrency, BigDecimal tenderAmount, BigDecimal fxRateSnapshot,
                      String reference, String terminalId, String last4) {
        this.posSaleId = posSaleId;
        this.method = method;
        this.amount = amount;
        this.tenderCurrency = tenderCurrency;
        this.tenderAmount = tenderAmount;
        this.fxRateSnapshot = fxRateSnapshot;
        this.reference = reference;
        this.terminalId = terminalId;
        this.last4 = last4;
    }
}

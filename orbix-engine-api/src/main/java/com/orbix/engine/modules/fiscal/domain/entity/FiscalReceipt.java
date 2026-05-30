package com.orbix.engine.modules.fiscal.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
import com.orbix.engine.modules.fiscal.domain.enums.FiscalStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row per POS sale that has entered the fiscalization pipeline.
 * Extends UidEntity — the uid is the external handle exposed via the
 * FiscalReceiptDto and the /api/v1/fiscal-receipts/uid/{uid} endpoint.
 *
 * <p>Counter fields (rctnum, gc, dc, znum) are TRA-mandated monotonic
 * values. They are populated after a successful EFDMS submission and
 * MUST NOT be reused — allocation is server-side with pessimistic
 * row-lock (future FiscalDevice aggregate). All counter fields are
 * tagged STUB until the TRA EFDMS spec is confirmed.
 */
@Entity
@Table(name = "fiscal_receipt",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_fiscal_receipt_uid",      columnNames = {"uid"}),
        @UniqueConstraint(name = "uk_fiscal_receipt_pos_sale", columnNames = {"pos_sale_id"})
    })
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
public class FiscalReceipt extends UidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fiscal_receipt_seq")
    @SequenceGenerator(name = "fiscal_receipt_seq", sequenceName = "fiscal_receipt_seq",
        allocationSize = 50)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "pos_sale_id", nullable = false, unique = true)
    private Long posSaleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FiscalStatus status = FiscalStatus.PENDING;

    /** The FiscalRegime code string that handled this receipt, e.g. "TZ_VFD" or "NONE". */
    @Column(length = 40)
    private String provider;

    // -----------------------------------------------------------------------
    // STUB fields — all values populated by the real EFDMS response.
    // Replace StubEfdmsClient with the real EfdmsClient when TRA spec lands.
    // -----------------------------------------------------------------------

    /** STUB: pending TRA EFDMS spec confirmation — RCTNUM (receipt counter, per device, monotonic). */
    @Column
    private Long rctnum;

    /** STUB: pending TRA EFDMS spec confirmation — GC (grand cumulative counter). */
    @Column
    private Long gc;

    /** STUB: pending TRA EFDMS spec confirmation — DC (daily counter, reset by Z-report). */
    @Column
    private Long dc;

    /** STUB: pending TRA EFDMS spec confirmation — ZNUM (Z-report / day number). */
    @Column
    private Integer znum;

    /** STUB: pending TRA EFDMS spec confirmation — TRA verification code printed on receipt. */
    @Column(length = 200)
    private String verificationCode;

    /** STUB: pending TRA EFDMS spec confirmation — https://verify.tra.go.tz/... URL. */
    @Column(length = 500)
    private String verifyUrl;

    /** STUB: pending TRA EFDMS spec confirmation — QR code payload (URL or encoded data). */
    @Column(length = 2000)
    private String qrPayload;

    /** STUB: pending TRA EFDMS spec confirmation — RSA device signature of submitted receipt XML. */
    @Column(length = 2000)
    private String signature;

    /** Raw EFDMS response body (truncated). STUB: format unknown until spec confirmed. */
    @Column(length = 4000)
    private String efdmsResponse;

    // -----------------------------------------------------------------------
    // Retry / observability
    // -----------------------------------------------------------------------

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Version
    private Integer version;

    public FiscalReceipt(Long posSaleId, Long companyId, Long branchId,
                         String provider, Long actorId) {
        this.posSaleId = posSaleId;
        this.companyId = companyId;
        this.branchId = branchId;
        this.provider = provider;
        this.status = FiscalStatus.PENDING;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    /** Apply a successful fiscalization result from the provider. */
    public void applyResult(FiscalStatus newStatus, Long rctnum, Long gc, Long dc,
                            Integer znum, String verificationCode, String verifyUrl,
                            String qrPayload, String signature, String efdmsResponse,
                            Long actorId) {
        this.status = newStatus;
        this.rctnum = rctnum;
        this.gc = gc;
        this.dc = dc;
        this.znum = znum;
        this.verificationCode = verificationCode;
        this.verifyUrl = verifyUrl;
        this.qrPayload = qrPayload;
        this.signature = signature;
        this.efdmsResponse = efdmsResponse;
        this.submittedAt = Instant.now();
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    /** Record a failed attempt (increments counter; outbox handles retry scheduling). */
    public void recordFailure(String error, Long actorId) {
        this.attemptCount++;
        this.lastError = error != null && error.length() > 1000
            ? error.substring(0, 997) + "..."
            : error;
        this.status = FiscalStatus.FAILED;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }

    /** Mark as NONE (NoOp regime — no fiscalization needed). */
    public void markNone(Long actorId) {
        this.status = FiscalStatus.NONE;
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}

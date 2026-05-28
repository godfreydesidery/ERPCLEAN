import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  inject,
  signal,
} from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { DebtService } from './debt.service';
import { DebtWriteOff, DebtWriteOffTargetKind } from './debt.models';

/**
 * Slice G.2 — Write-off creation modal.
 *
 * Opened from the AR drill-down ({@link DebtCustomerComponent}) and the
 * AP drill-down ({@link DebtSupplierComponent}).  Rendered as a Bootstrap
 * modal overlay; the parent controls visibility via the {@code visible}
 * input.  On a successful POST the parent receives a {@code writeOffCreated}
 * event and should refresh its open-invoices list.
 *
 * Accessibility:
 *  - {@code role="dialog"}, {@code aria-modal="true"}, labelled by
 *    {@code aria-labelledby}.
 *  - Focus is trapped inside the dialog while open (Escape closes it).
 *  - Every input has an associated {@code <label>}.
 */
@Component({
  selector: 'orbix-debt-write-off-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, DecimalPipe],
  template: `
    @if (visible) {
      <!-- Backdrop -->
      <div class="modal-backdrop fade show" (click)="onCancel()" aria-hidden="true"></div>

      <!-- Dialog -->
      <div class="modal d-block"
           role="dialog"
           aria-modal="true"
           aria-labelledby="writeOffModalTitle"
           (keydown.escape)="onCancel()">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content shadow-lg">

            <div class="modal-header border-bottom">
              <h2 class="modal-title h5 fw-bold mb-0" id="writeOffModalTitle">
                Write off invoice
              </h2>
              <button type="button"
                      class="btn-close"
                      aria-label="Close write-off dialog"
                      (click)="onCancel()">
              </button>
            </div>

            <div class="modal-body">

              <!-- Invoice context banner -->
              <div class="alert alert-info py-2 px-3 mb-3 small">
                <strong>Invoice:</strong> {{ targetInvoiceNumber ?? targetInvoiceUid }}
                &nbsp;&bull;&nbsp;
                <strong>Outstanding:</strong> {{ outstanding | number:'1.0-2' }}
              </div>

              <!-- Success banner (after POST) -->
              @if (successMessage()) {
                <div class="alert alert-success d-flex align-items-center gap-2 py-2" role="status">
                  <i class="bi bi-check-circle-fill"></i>
                  <span class="flex-grow-1">{{ successMessage() }}</span>
                </div>
              }

              <!-- Generic error banner -->
              @if (bannerError()) {
                <div class="alert alert-danger d-flex align-items-center gap-2 py-2" role="alert">
                  <i class="bi bi-exclamation-triangle-fill"></i>
                  <span class="flex-grow-1">{{ bannerError() }}</span>
                  <button type="button" class="btn-close btn-sm"
                          aria-label="Dismiss error"
                          (click)="bannerError.set(null)"></button>
                </div>
              }

              @if (!successMessage()) {
                <form (ngSubmit)="onSubmit()" id="writeOffForm" novalidate>

                  <!-- Amount -->
                  <div class="mb-3">
                    <label for="writeOffAmount" class="form-label fw-semibold">
                      Write-off amount
                      <span class="text-danger" aria-hidden="true">*</span>
                    </label>
                    <input id="writeOffAmount"
                           type="number"
                           class="form-control"
                           [class.is-invalid]="amountError()"
                           name="writeOffAmount"
                           [min]="0.01"
                           [max]="outstanding"
                           step="0.01"
                           aria-describedby="writeOffAmountHelp"
                           [(ngModel)]="amountDraft"
                           required>
                    <div id="writeOffAmountHelp" class="form-text">
                      Min 0.01 — max {{ outstanding | number:'1.0-2' }} (outstanding balance).
                    </div>
                    @if (amountError()) {
                      <div class="invalid-feedback" role="alert">{{ amountError() }}</div>
                    }
                  </div>

                  <!-- Reason -->
                  <div class="mb-1">
                    <label for="writeOffReason" class="form-label fw-semibold">
                      Reason
                      <span class="text-danger" aria-hidden="true">*</span>
                    </label>
                    <textarea id="writeOffReason"
                              class="form-control"
                              [class.is-invalid]="reasonError()"
                              name="writeOffReason"
                              rows="4"
                              maxlength="2000"
                              placeholder="Explain why this debt is being written off…"
                              aria-describedby="writeOffReasonCount"
                              [(ngModel)]="reasonDraft"
                              required></textarea>
                    <p id="writeOffReasonCount" class="form-text">
                      {{ reasonDraft.length }} / 2000
                    </p>
                    @if (reasonError()) {
                      <div class="invalid-feedback d-block" role="alert">{{ reasonError() }}</div>
                    }
                  </div>

                </form>
              }

            </div>

            <div class="modal-footer border-top">
              <button type="button"
                      class="btn btn-outline-secondary"
                      (click)="onCancel()">
                {{ successMessage() ? 'Close' : 'Cancel' }}
              </button>
              @if (!successMessage()) {
                <button type="submit"
                        form="writeOffForm"
                        class="btn btn-danger"
                        [disabled]="submitting()">
                  @if (submitting()) {
                    <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
                  }
                  Write off
                </button>
              }
            </div>

          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    :host { display: contents; }
    .modal-backdrop { z-index: 1040; }
    .modal { z-index: 1050; }
  `]
})
export class DebtWriteOffModalComponent implements OnChanges {
  private readonly debt = inject(DebtService);

  /** Whether the modal is currently open. Parent controls this. */
  @Input() visible = false;

  /** Which ledger side — CUSTOMER_INVOICE or SUPPLIER_INVOICE. */
  @Input() targetKind: DebtWriteOffTargetKind = 'CUSTOMER_INVOICE';

  /** UID of the invoice being written off. */
  @Input() targetInvoiceUid = '';

  /** Human-readable invoice number for the context banner. */
  @Input() targetInvoiceNumber: string | null = null;

  /** Default amount — should equal totalAmount − paidAmount from the invoice row. */
  @Input() outstanding = 0;

  /** Emitted after a successful POST; parent refreshes its list. */
  @Output() readonly writeOffCreated = new EventEmitter<DebtWriteOff>();

  /** Emitted when the user closes the modal without a successful write-off. */
  @Output() readonly closed = new EventEmitter<void>();

  protected amountDraft: number | null = null;
  protected reasonDraft = '';

  protected readonly submitting = signal(false);
  protected readonly amountError = signal<string | null>(null);
  protected readonly reasonError = signal<string | null>(null);
  protected readonly bannerError = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  /** Re-seed the form whenever the modal opens with new data. */
  ngOnChanges(): void {
    if (this.visible) {
      this.amountDraft = this.outstanding;
      this.reasonDraft = '';
      this.amountError.set(null);
      this.reasonError.set(null);
      this.bannerError.set(null);
      this.successMessage.set(null);
    }
  }

  protected onCancel(): void {
    if (this.submitting()) return;
    this.closed.emit();
  }

  protected onSubmit(): void {
    // Client-side validation
    this.amountError.set(null);
    this.reasonError.set(null);
    this.bannerError.set(null);

    const amount = Number(this.amountDraft);
    let hasError = false;

    if (!this.amountDraft || isNaN(amount) || amount < 0.01) {
      this.amountError.set('Amount must be at least 0.01.');
      hasError = true;
    } else if (amount > this.outstanding) {
      this.amountError.set(`Amount cannot exceed the outstanding balance (${this.outstanding}).`);
      hasError = true;
    }

    if (!this.reasonDraft.trim()) {
      this.reasonError.set('Reason is required.');
      hasError = true;
    } else if (this.reasonDraft.length > 2000) {
      this.reasonError.set('Reason must not exceed 2000 characters.');
      hasError = true;
    }

    if (hasError) return;

    this.submitting.set(true);
    this.debt.createWriteOff({
      targetKind: this.targetKind,
      targetInvoiceUid: this.targetInvoiceUid,
      amount,
      reason: this.reasonDraft.trim(),
    }).subscribe({
      next: (writeOff: DebtWriteOff) => {
        this.submitting.set(false);
        this.successMessage.set(
          writeOff.status === 'POSTED'
            ? 'Write-off auto-posted. The invoice is now fully settled.'
            : 'Write-off submitted and awaiting approval.'
        );
        this.writeOffCreated.emit(writeOff);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        if (err.status === 409) {
          // Dual-approval gate: same user cannot approve their own above-threshold write-off
          this.bannerError.set(
            'A different user must approve write-offs above the threshold. ' +
            'Ask a colleague with the Approve permission to action this request.'
          );
        } else if (err.status === 422) {
          // Amount validation from backend
          const body = err.error;
          const msg = (body && typeof body === 'object' && 'message' in body)
            ? String((body as { message: unknown }).message)
            : null;
          this.amountError.set(msg ?? 'Invalid amount — check the outstanding balance.');
        } else {
          this.bannerError.set(this.extractMessage(err, 'Could not submit the write-off. Please try again.'));
        }
      },
    });
  }

  private extractMessage(err: HttpErrorResponse, fallback: string): string {
    const body = err.error;
    if (body && typeof body === 'object' && 'message' in body && body.message) {
      return String((body as { message: unknown }).message);
    }
    return fallback;
  }
}

import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  inject,
  signal,
} from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { SalesService } from './sales.service';
import { ApplyCreditNoteRequest, CustomerCreditNote } from './sales.models';
import { OpenInvoiceRow } from '../debt/debt.models';

/**
 * Slice H — Apply a customer credit note to an open invoice.
 *
 * Opened from the credit-notes table in {@link ReturnsComponent}.
 * Parent controls visibility via the {@code visible} input.
 * On a successful POST the parent receives a {@code creditNoteApplied}
 * event and should refresh its credit-note list.
 *
 * Four states:
 *  - loading  — fetching open invoices
 *  - empty    — no open invoices for this customer (submit disabled)
 *  - error    — dismissable banner (fetch or submit failure)
 *  - populated — invoice picker + amount field
 *
 * Accessibility:
 *  - {@code role="dialog"}, {@code aria-modal="true"}, labelled by
 *    {@code aria-labelledby}.
 *  - Focus is trapped inside the dialog while open (Escape closes it).
 *  - Every input / select has an associated {@code <label>}.
 */
@Component({
  selector: 'orbix-credit-note-apply-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, DecimalPipe],
  template: `
    @if (visible) {
      <!-- Backdrop -->
      <div class="modal-backdrop fade show" (click)="onCancel()" aria-hidden="true"></div>

      <!-- Dialog -->
      <div class="modal d-block"
           role="dialog"
           aria-modal="true"
           aria-labelledby="cnApplyModalTitle"
           (keydown.escape)="onCancel()">
        <div class="modal-dialog modal-dialog-centered modal-lg">
          <div class="modal-content shadow-lg">

            <div class="modal-header border-bottom">
              <h2 class="modal-title h5 fw-bold mb-0" id="cnApplyModalTitle">
                Apply credit note
              </h2>
              <button type="button"
                      class="btn-close"
                      aria-label="Close apply-credit dialog"
                      (click)="onCancel()">
              </button>
            </div>

            <div class="modal-body">

              <!-- Credit note context banner -->
              <div class="alert alert-info py-2 px-3 mb-3 small">
                <strong>Credit note:</strong> {{ creditNote.number }}
                &nbsp;&bull;&nbsp;
                <strong>Available:</strong> {{ creditNote.availableAmount | number:'1.2-2' }}
                {{ creditNote.currencyCode }}
              </div>

              <!-- Success banner -->
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
                @if (loadingInvoices()) {
                  <div class="d-flex align-items-center justify-content-center py-4 gap-2 text-secondary">
                    <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                    <span>Loading open invoices…</span>
                  </div>
                } @else if (openInvoices().length === 0) {
                  <div class="text-center py-4 text-secondary small">
                    <i class="bi bi-inbox fs-3 d-block mb-2"></i>
                    No open invoices found for this customer. Credit cannot be applied.
                  </div>
                } @else {
                  <form (ngSubmit)="onSubmit()" id="cnApplyForm" novalidate>

                    <!-- Invoice picker -->
                    <div class="mb-3">
                      <label for="cnInvoicePicker" class="form-label fw-semibold">
                        Open invoice
                        <span class="text-danger" aria-hidden="true">*</span>
                      </label>
                      <select id="cnInvoicePicker"
                              class="form-select"
                              [class.is-invalid]="invoiceError()"
                              name="cnInvoice"
                              [(ngModel)]="selectedInvoiceUid"
                              (ngModelChange)="onInvoiceSelected($event)"
                              required
                              aria-describedby="cnInvoiceHelp">
                        <option value="">— select an invoice —</option>
                        @for (inv of openInvoices(); track inv.invoiceUid) {
                          <option [value]="inv.invoiceUid">
                            {{ inv.number }} · outstanding {{ inv.outstanding | number:'1.2-2' }}
                            ({{ inv.status }})
                          </option>
                        }
                      </select>
                      <div id="cnInvoiceHelp" class="form-text">
                        Only invoices with an outstanding balance are shown.
                      </div>
                      @if (invoiceError()) {
                        <div class="invalid-feedback" role="alert">{{ invoiceError() }}</div>
                      }
                    </div>

                    <!-- Amount -->
                    <div class="mb-1">
                      <label for="cnApplyAmount" class="form-label fw-semibold">
                        Amount to apply
                        <span class="text-danger" aria-hidden="true">*</span>
                      </label>
                      <input id="cnApplyAmount"
                             type="number"
                             class="form-control"
                             [class.is-invalid]="amountError()"
                             name="cnAmount"
                             [min]="0.01"
                             [max]="maxAmount()"
                             step="0.01"
                             aria-describedby="cnApplyAmountHelp"
                             [(ngModel)]="amountDraft"
                             required>
                      <div id="cnApplyAmountHelp" class="form-text">
                        Min 0.01 — max {{ maxAmount() | number:'1.2-2' }}
                        (lesser of credit available and invoice outstanding).
                      </div>
                      @if (amountError()) {
                        <div class="invalid-feedback" role="alert">{{ amountError() }}</div>
                      }
                    </div>

                  </form>
                }
              }

            </div>

            <div class="modal-footer border-top">
              <button type="button"
                      class="btn btn-outline-secondary"
                      (click)="onCancel()">
                {{ successMessage() ? 'Close' : 'Cancel' }}
              </button>
              @if (!successMessage() && openInvoices().length > 0 && !loadingInvoices()) {
                <button type="submit"
                        form="cnApplyForm"
                        class="btn btn-primary"
                        [disabled]="submitting()">
                  @if (submitting()) {
                    <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
                  }
                  Apply credit
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
export class CreditNoteApplyModalComponent implements OnChanges {
  private readonly sales = inject(SalesService);

  /** Whether the modal is currently open. Parent controls this. */
  @Input() visible = false;

  /** The credit note being applied. */
  @Input() creditNote!: CustomerCreditNote;

  /** UID of the customer whose open invoices should be listed. */
  @Input() customerUid = '';

  /** Emitted after a successful POST; parent refreshes its credit-note list. */
  @Output() readonly creditNoteApplied = new EventEmitter<CustomerCreditNote>();

  /** Emitted when the user closes without a successful apply. */
  @Output() readonly closed = new EventEmitter<void>();

  protected selectedInvoiceUid = '';
  protected amountDraft: number | null = null;

  protected readonly loadingInvoices = signal(false);
  protected readonly openInvoices = signal<OpenInvoiceRow[]>([]);
  protected readonly submitting = signal(false);
  protected readonly invoiceError = signal<string | null>(null);
  protected readonly amountError = signal<string | null>(null);
  protected readonly bannerError = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);

  /** Max applicable amount = min(creditNote.availableAmount, selectedInvoice.outstanding). */
  protected maxAmount(): number {
    const inv = this.openInvoices().find(i => i.invoiceUid === this.selectedInvoiceUid);
    if (!inv) return this.creditNote?.availableAmount ?? 0;
    return Math.min(this.creditNote.availableAmount, inv.outstanding);
  }

  /** Re-seed the form and fetch open invoices whenever the modal opens. */
  ngOnChanges(): void {
    if (this.visible && this.customerUid) {
      this.resetForm();
      this.fetchInvoices();
    }
  }

  protected onInvoiceSelected(uid: string): void {
    this.selectedInvoiceUid = uid;
    // Default amount to max applicable for the newly selected invoice
    this.amountDraft = this.maxAmount() > 0 ? this.maxAmount() : null;
    this.amountError.set(null);
  }

  protected onCancel(): void {
    if (this.submitting()) return;
    this.closed.emit();
  }

  protected onSubmit(): void {
    this.invoiceError.set(null);
    this.amountError.set(null);
    this.bannerError.set(null);

    let hasError = false;

    if (!this.selectedInvoiceUid) {
      this.invoiceError.set('Please select an open invoice.');
      hasError = true;
    }

    const amount = Number(this.amountDraft);
    if (!this.amountDraft || isNaN(amount) || amount < 0.01) {
      this.amountError.set('Amount must be at least 0.01.');
      hasError = true;
    } else if (amount > this.maxAmount()) {
      this.amountError.set(
        `Amount cannot exceed ${this.maxAmount().toFixed(2)} ` +
        `(lesser of credit available and invoice outstanding).`
      );
      hasError = true;
    }

    if (hasError) return;

    const request: ApplyCreditNoteRequest = {
      salesInvoiceUid: this.selectedInvoiceUid,
      amount,
    };

    this.submitting.set(true);
    this.sales.applyCreditNote(this.creditNote.uid, request).subscribe({
      next: (updated: CustomerCreditNote) => {
        this.submitting.set(false);
        this.successMessage.set(
          updated.status === 'FULLY_ALLOCATED'
            ? `Credit note fully allocated. All available credit has been applied.`
            : `Applied ${amount.toFixed(2)} ${updated.currencyCode}. ` +
              `Remaining credit: ${updated.availableAmount.toFixed(2)}.`
        );
        this.creditNoteApplied.emit(updated);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        if (err.status === 409) {
          this.bannerError.set(
            'Credit note is fully allocated — no remaining credit to apply.'
          );
        } else if (err.status === 422) {
          const body = err.error;
          const msg = (body && typeof body === 'object' && 'message' in body)
            ? String((body as { message: unknown }).message)
            : null;
          this.amountError.set(msg ?? 'Invalid amount — check the outstanding balance and credit available.');
        } else {
          this.bannerError.set(this.extractMessage(err, 'Could not apply the credit note. Please try again.'));
        }
      },
    });
  }

  private fetchInvoices(): void {
    this.loadingInvoices.set(true);
    this.openInvoices.set([]);
    const obs = this.sales.getOpenInvoicesForCustomer(this.customerUid);
    if (!obs) {
      this.loadingInvoices.set(false);
      return;
    }
    obs.subscribe({
      next: rows => {
        this.loadingInvoices.set(false);
        this.openInvoices.set(rows);
      },
      error: (err: HttpErrorResponse) => {
        this.loadingInvoices.set(false);
        this.bannerError.set(this.extractMessage(err, 'Could not load open invoices.'));
      },
    });
  }

  private resetForm(): void {
    this.selectedInvoiceUid = '';
    this.amountDraft = null;
    this.invoiceError.set(null);
    this.amountError.set(null);
    this.bannerError.set(null);
    this.successMessage.set(null);
  }

  private extractMessage(err: HttpErrorResponse, fallback: string): string {
    const body = err.error;
    if (body && typeof body === 'object' && 'message' in body && body.message) {
      return String((body as { message: unknown }).message);
    }
    return fallback;
  }
}

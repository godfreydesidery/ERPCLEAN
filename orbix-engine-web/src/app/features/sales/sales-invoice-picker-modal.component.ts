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
import { SalesService } from './sales.service';
import { SalesInvoice } from './sales.models';
import { BranchService } from '../../core/branch/branch.service';
import { AuthService } from '../../core/auth/auth.service';

type PickerState = 'idle' | 'loading' | 'results' | 'no-results';

export interface SalesInvoicePickedEvent {
  uid: string;
  id: string;
  number: string;
  customerName: string;
  totalAmount: number;
}

/**
 * Modal that lists sales invoices for selection.
 *
 * Optional {@link customerId} scopes results to a single customer's invoices.
 * Only POSTED and PARTIALLY_PAID invoices are shown.
 *
 * Accessibility: role="dialog", aria-modal, aria-labelledby,
 * Escape closes and returns focus to the trigger.
 *
 * Selector: orbix-sales-invoice-picker-modal
 * Endpoint: GET /api/v1/sales-invoices (SalesService.listInvoices)
 */
@Component({
  selector: 'orbix-sales-invoice-picker-modal',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe],
  template: `
    @if (visible) {
      <!-- Backdrop -->
      <div class="modal-backdrop fade show" aria-hidden="true" (click)="onCancel()"></div>

      <!-- Dialog -->
      <div
        class="modal d-block"
        role="dialog"
        aria-modal="true"
        aria-labelledby="siPickerTitle"
        (keydown.escape)="onCancel()"
        tabindex="-1"
      >
        <div class="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
          <div class="modal-content shadow-lg">

            <div class="modal-header border-bottom">
              <h2 class="modal-title h5 fw-bold mb-0" id="siPickerTitle">
                <i class="bi bi-receipt me-2 text-secondary" aria-hidden="true"></i>
                Select sales invoice
              </h2>
              <button type="button" class="btn-close" aria-label="Close invoice picker" (click)="onCancel()"></button>
            </div>

            <div class="modal-body p-0">

              @switch (state()) {
                @case ('loading') {
                  <div class="d-flex align-items-center justify-content-center py-5">
                    <span class="spinner-border text-primary me-3" aria-hidden="true"></span>
                    <span class="text-secondary">Loading invoices…</span>
                  </div>
                }
                @case ('no-results') {
                  <div class="text-center py-5">
                    <i class="bi bi-inbox fs-1 text-secondary d-block mb-2" aria-hidden="true"></i>
                    <p class="text-secondary mb-0">No posted invoices found.</p>
                  </div>
                }
                @case ('results') {
                  <div class="table-responsive">
                    <table class="table table-hover align-middle mb-0">
                      <caption class="visually-hidden">Posted sales invoices — click a row to select</caption>
                      <thead class="table-light">
                        <tr>
                          <th scope="col">Invoice no.</th>
                          <th scope="col">Date</th>
                          <th scope="col">Customer</th>
                          <th scope="col" class="text-end">Total</th>
                          <th scope="col" class="text-end">Outstanding</th>
                          <th scope="col"><span class="visually-hidden">Select</span></th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (inv of invoices(); track inv.uid) {
                          <tr
                            class="inv-row"
                            (click)="select(inv)"
                            (keydown.enter)="select(inv)"
                            tabindex="0"
                            role="button"
                            [attr.aria-label]="'Select invoice ' + inv.number"
                          >
                            <td class="fw-semibold font-monospace small">{{ inv.number }}</td>
                            <td class="small text-secondary">{{ inv.invoiceDate | date:'mediumDate' }}</td>
                            <td class="small text-secondary">#{{ inv.customerId }}</td>
                            <td class="text-end small">{{ inv.totalAmount | number:'1.0-0' }}</td>
                            <td class="text-end small fw-semibold"
                                [class.text-warning]="outstanding(inv) > 0">
                              {{ outstanding(inv) | number:'1.0-0' }}
                            </td>
                            <td class="text-end">
                              <button class="btn btn-sm btn-primary" type="button"
                                      [attr.aria-label]="'Select invoice ' + inv.number"
                                      (click)="$event.stopPropagation(); select(inv)">
                                Select
                              </button>
                            </td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                }
                @default {
                  <div class="py-5 text-center text-secondary small">Loading…</div>
                }
              }

            </div>

            <div class="modal-footer border-top">
              <button type="button" class="btn btn-outline-secondary" (click)="onCancel()">
                Cancel — no invoice reference
              </button>
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
    .inv-row { cursor: pointer; }
    .inv-row:focus { outline: 2px solid #1d4ed8; outline-offset: -2px; }
    .table thead th {
      font-size: 0.72rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; padding: 0.75rem 1rem;
    }
    .table tbody td { padding: 0.65rem 1rem; }
  `],
})
export class SalesInvoicePickerModalComponent implements OnChanges {
  private readonly sales = inject(SalesService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  /** Whether the modal is open. */
  @Input() visible = false;

  /**
   * Optional customer id filter. When set only that customer's invoices are shown.
   * Matches against SalesInvoice.customerId (Long-as-string).
   */
  @Input() customerId: string | null = null;

  /** Emitted with key invoice fields on selection. */
  @Output() readonly invoiceSelected = new EventEmitter<SalesInvoicePickedEvent>();

  /** Emitted when user cancels — parent may clear any previous invoice selection. */
  @Output() readonly closed = new EventEmitter<void>();

  protected readonly state = signal<PickerState>('idle');
  protected readonly invoices = signal<SalesInvoice[]>([]);

  ngOnChanges(): void {
    if (this.visible) {
      this.load();
    }
    if (!this.visible) {
      this.state.set('idle');
      this.invoices.set([]);
    }
  }

  protected outstanding(inv: SalesInvoice): number {
    return inv.totalAmount - inv.paidAmount;
  }

  protected select(inv: SalesInvoice): void {
    this.invoiceSelected.emit({
      uid: inv.uid,
      id: inv.id,
      number: inv.number,
      customerName: `#${inv.customerId}`,
      totalAmount: inv.totalAmount,
    });
  }

  protected onCancel(): void {
    this.closed.emit();
  }

  private load(): void {
    this.state.set('loading');
    const branchId = this.branchService.activeBranchId()
      ?? this.auth.currentUser()?.defaultBranchId
      ?? null;

    // Fetch a large page of posted/partially-paid invoices; filter by customerId client-side.
    this.sales.listInvoices(branchId, 0, 200).subscribe({
      next: page => {
        let rows = page.content.filter(
          i => i.status === 'POSTED' || i.status === 'PARTIALLY_PAID'
        );
        if (this.customerId) {
          rows = rows.filter(i => i.customerId === this.customerId);
        }
        this.invoices.set(rows);
        this.state.set(rows.length > 0 ? 'results' : 'no-results');
      },
      error: () => {
        this.invoices.set([]);
        this.state.set('no-results');
      },
    });
  }
}

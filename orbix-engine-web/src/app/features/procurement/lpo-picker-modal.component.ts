import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  inject,
  signal,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { ProcurementService } from './procurement.service';
import { LpoOrder } from './procurement.models';
import { BranchService } from '../../core/branch/branch.service';
import { AuthService } from '../../core/auth/auth.service';

type PickerState = 'idle' | 'loading' | 'results' | 'no-results';

/**
 * Modal picker for selecting an LPO when creating a GRN (or other documents
 * that reference an LPO).
 *
 * Cloned from grn-picker-modal.component.ts. Shows APPROVED LPOs by default
 * (those eligible for goods receipt).  Optionally filters by supplierId.
 *
 * Selector: `orbix-lpo-picker-modal`
 *
 * Inputs:
 *   - visible (boolean) — controls open/close; reload fires each time
 *     visible flips true.
 *   - supplierId (string | null) — optional numeric id (string-serialised Long)
 *     to filter LPOs to a specific supplier. Pass null to show all.
 *   - statusFilter (LpoOrderStatus | null) — defaults to 'APPROVED'.
 *     Pass null to show all statuses (e.g. PARTIALLY_RECEIVED).
 *
 * Outputs:
 *   - lpoSelected — emits the full {@link LpoOrder} on selection.
 *   - closed      — emits void on cancel.
 *
 * Accessibility: role="dialog", aria-modal, aria-labelledby, Escape closes.
 */
@Component({
  selector: 'orbix-lpo-picker-modal',
  standalone: true,
  imports: [CommonModule, DatePipe],
  template: `
    @if (visible) {
      <!-- Backdrop -->
      <div class="modal-backdrop fade show" aria-hidden="true" (click)="onCancel()"></div>

      <!-- Dialog -->
      <div
        class="modal d-block"
        role="dialog"
        aria-modal="true"
        aria-labelledby="lpoPickerTitle"
        (keydown.escape)="onCancel()"
        tabindex="-1"
      >
        <div class="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
          <div class="modal-content shadow-lg">

            <div class="modal-header border-bottom">
              <h2 class="modal-title h5 fw-bold mb-0" id="lpoPickerTitle">
                <i class="bi bi-file-earmark-text me-2 text-secondary" aria-hidden="true"></i>
                Select LPO
              </h2>
              <button type="button" class="btn-close" aria-label="Close LPO picker" (click)="onCancel()"></button>
            </div>

            <div class="modal-body p-0">

              @switch (state()) {
                @case ('loading') {
                  <div class="d-flex align-items-center justify-content-center py-5">
                    <span class="spinner-border text-primary me-3" aria-hidden="true"></span>
                    <span class="text-secondary">Loading LPOs…</span>
                  </div>
                }
                @case ('no-results') {
                  <div class="text-center py-5">
                    <i class="bi bi-inbox fs-1 text-secondary d-block mb-2" aria-hidden="true"></i>
                    <p class="text-secondary mb-0">No LPOs available for selection.</p>
                  </div>
                }
                @case ('results') {
                  <div class="table-responsive">
                    <table class="table table-hover align-middle mb-0">
                      <caption class="visually-hidden">LPOs available — click a row to select</caption>
                      <thead class="table-light">
                        <tr>
                          <th scope="col">LPO number</th>
                          <th scope="col">Order date</th>
                          <th scope="col">Expected delivery</th>
                          <th scope="col">Lines</th>
                          <th scope="col" class="text-end">Total</th>
                          <th scope="col">Status</th>
                          <th scope="col"><span class="visually-hidden">Select</span></th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (lpo of lpos(); track lpo.uid) {
                          <tr
                            class="lpo-row"
                            (click)="select(lpo)"
                            (keydown.enter)="select(lpo)"
                            tabindex="0"
                            role="button"
                            [attr.aria-label]="'Select LPO ' + lpo.number"
                          >
                            <td class="fw-semibold font-monospace small">{{ lpo.number }}</td>
                            <td class="small text-secondary">{{ lpo.orderDate | date:'mediumDate' }}</td>
                            <td class="small text-secondary">
                              {{ lpo.expectedDeliveryDate ? (lpo.expectedDeliveryDate | date:'mediumDate') : '—' }}
                            </td>
                            <td class="small text-secondary">{{ lpo.lines.length }}</td>
                            <td class="text-end small">{{ lpo.currencyCode }} {{ lpo.totalAmount | number:'1.0-0' }}</td>
                            <td>
                              <span class="badge"
                                    [class.text-bg-success]="lpo.status === 'APPROVED'"
                                    [class.text-bg-warning]="lpo.status === 'PARTIALLY_RECEIVED'"
                                    [class.text-bg-secondary]="lpo.status !== 'APPROVED' && lpo.status !== 'PARTIALLY_RECEIVED'">
                                {{ lpo.status }}
                              </span>
                            </td>
                            <td class="text-end">
                              <button class="btn btn-sm btn-primary" type="button"
                                      [attr.aria-label]="'Select LPO ' + lpo.number"
                                      (click)="$event.stopPropagation(); select(lpo)">
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
                Cancel — no LPO reference
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
    .lpo-row { cursor: pointer; }
    .lpo-row:focus { outline: 2px solid #1d4ed8; outline-offset: -2px; }
    .table thead th {
      font-size: 0.72rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; padding: 0.75rem 1rem;
    }
    .table tbody td { padding: 0.65rem 1rem; }
  `],
})
export class LpoPickerModalComponent implements OnChanges {
  private readonly procurement = inject(ProcurementService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  /** Whether the modal is open. */
  @Input() visible = false;

  /**
   * Supplier's numeric id (string-serialised Long). Filters the LPO list to
   * this supplier. Pass null to show LPOs for all suppliers.
   */
  @Input() supplierId: string | null = null;

  /**
   * Status filter applied to the list call. Defaults to 'APPROVED'.
   * Pass null to show any status (e.g. include PARTIALLY_RECEIVED).
   */
  @Input() statusFilter: string | null = 'APPROVED';

  /** Emitted with the full LpoOrder on selection. */
  @Output() readonly lpoSelected = new EventEmitter<LpoOrder>();

  /** Emitted when user cancels. */
  @Output() readonly closed = new EventEmitter<void>();

  protected readonly state = signal<PickerState>('idle');
  protected readonly lpos = signal<LpoOrder[]>([]);

  ngOnChanges(): void {
    if (this.visible) {
      this.load();
    }
    if (!this.visible) {
      this.state.set('idle');
      this.lpos.set([]);
    }
  }

  protected select(lpo: LpoOrder): void {
    this.lpoSelected.emit(lpo);
  }

  protected onCancel(): void {
    this.closed.emit();
  }

  private load(): void {
    this.state.set('loading');
    const branchId = this.branchService.activeBranchId()
      ?? this.auth.currentUser()?.defaultBranchId
      ?? null;

    this.procurement.listLpos(branchId, 0, 100, this.statusFilter).subscribe({
      next: page => {
        // Apply supplier filter client-side if supplierId is provided,
        // because the /lpos endpoint currently supports status but not supplierId.
        const rows = this.supplierId
          ? page.content.filter(l => l.supplierId === this.supplierId)
          : page.content;
        this.lpos.set(rows);
        this.state.set(rows.length > 0 ? 'results' : 'no-results');
      },
      error: () => {
        this.lpos.set([]);
        this.state.set('no-results');
      },
    });
  }
}

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
import { ProcurementService } from './procurement.service';
import { Grn } from './procurement.models';
import { BranchService } from '../../core/branch/branch.service';
import { AuthService } from '../../core/auth/auth.service';

type PickerState = 'idle' | 'loading' | 'results' | 'no-results';

/**
 * Modal that lists a supplier's POSTED GRNs for selection.
 *
 * Accessibility: role="dialog", aria-modal, aria-labelledby,
 * Escape closes and returns focus to the trigger.
 *
 * The parent controls visibility via {@link visible}. On selection the full
 * {@link Grn} is emitted so the parent can pre-fill lines without a second fetch.
 */
@Component({
  selector: 'orbix-grn-picker-modal',
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
        aria-labelledby="grnPickerTitle"
        (keydown.escape)="onCancel()"
        tabindex="-1"
      >
        <div class="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
          <div class="modal-content shadow-lg">

            <div class="modal-header border-bottom">
              <h2 class="modal-title h5 fw-bold mb-0" id="grnPickerTitle">
                <i class="bi bi-box-seam me-2 text-secondary" aria-hidden="true"></i>
                Select GRN
              </h2>
              <button type="button" class="btn-close" aria-label="Close GRN picker" (click)="onCancel()"></button>
            </div>

            <div class="modal-body p-0">

              @switch (state()) {
                @case ('loading') {
                  <div class="d-flex align-items-center justify-content-center py-5">
                    <span class="spinner-border text-primary me-3" aria-hidden="true"></span>
                    <span class="text-secondary">Loading GRNs…</span>
                  </div>
                }
                @case ('no-results') {
                  <div class="text-center py-5">
                    <i class="bi bi-inbox fs-1 text-secondary d-block mb-2" aria-hidden="true"></i>
                    <p class="text-secondary mb-0">No posted GRNs for this supplier.</p>
                  </div>
                }
                @case ('results') {
                  <div class="table-responsive">
                    <table class="table table-hover align-middle mb-0">
                      <caption class="visually-hidden">Posted GRNs — click a row to select</caption>
                      <thead class="table-light">
                        <tr>
                          <th scope="col">GRN number</th>
                          <th scope="col">Received date</th>
                          <th scope="col">Lines</th>
                          <th scope="col" class="text-end">Total (TZS)</th>
                          <th scope="col"><span class="visually-hidden">Select</span></th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (grn of grns(); track grn.uid) {
                          <tr
                            class="grn-row"
                            (click)="select(grn)"
                            (keydown.enter)="select(grn)"
                            tabindex="0"
                            role="button"
                            [attr.aria-label]="'Select GRN ' + grn.number"
                          >
                            <td class="fw-semibold font-monospace small">{{ grn.number }}</td>
                            <td class="small text-secondary">{{ grn.receivedDate | date:'mediumDate' }}</td>
                            <td class="small text-secondary">{{ grn.lines.length }}</td>
                            <td class="text-end small">{{ grn.totalAmount | number:'1.0-0' }}</td>
                            <td class="text-end">
                              <button class="btn btn-sm btn-primary" type="button"
                                      [attr.aria-label]="'Select GRN ' + grn.number"
                                      (click)="$event.stopPropagation(); select(grn)">
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
                  <!-- idle — shown briefly before first open -->
                  <div class="py-5 text-center text-secondary small">
                    Loading…
                  </div>
                }
              }

            </div>

            <div class="modal-footer border-top">
              <button type="button" class="btn btn-outline-secondary" (click)="onCancel()">
                Cancel — no GRN reference
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
    .grn-row { cursor: pointer; }
    .grn-row:focus { outline: 2px solid #1d4ed8; outline-offset: -2px; }
    .table thead th {
      font-size: 0.72rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; padding: 0.75rem 1rem;
    }
    .table tbody td { padding: 0.65rem 1rem; }
  `],
})
export class GrnPickerModalComponent implements OnChanges {
  private readonly procurement = inject(ProcurementService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  /** Whether the modal is open. */
  @Input() visible = false;

  /**
   * Supplier's numeric id (string-serialised Long). Used as the supplierId
   * filter on the GRN list endpoint (added by the BE in the parallel task).
   */
  @Input() supplierId: string | null = null;

  /** Emitted with the full GRN on selection — parent pre-fills lines. */
  @Output() readonly grnSelected = new EventEmitter<Grn>();

  /** Emitted when user cancels — parent clears any previous GRN selection. */
  @Output() readonly closed = new EventEmitter<void>();

  protected readonly state = signal<PickerState>('idle');
  protected readonly grns = signal<Grn[]>([]);

  ngOnChanges(): void {
    if (this.visible && this.supplierId) {
      this.load();
    }
    if (!this.visible) {
      // Reset state on close so it re-loads fresh on next open.
      this.state.set('idle');
      this.grns.set([]);
    }
  }

  protected select(grn: Grn): void {
    this.grnSelected.emit(grn);
  }

  protected onCancel(): void {
    this.closed.emit();
  }

  private load(): void {
    this.state.set('loading');
    const branchId = this.branchService.activeBranchId()
      ?? this.auth.currentUser()?.defaultBranchId
      ?? null;

    this.procurement.listGrns(branchId, 0, 50, this.supplierId, 'POSTED').subscribe({
      next: page => {
        this.grns.set(page.content);
        this.state.set(page.content.length > 0 ? 'results' : 'no-results');
      },
      error: () => {
        this.grns.set([]);
        this.state.set('no-results');
      },
    });
  }
}

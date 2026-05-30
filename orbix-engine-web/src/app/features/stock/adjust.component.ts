import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { CoreLookupService } from '../../core/ui/core-lookup.service';
import { UserPickerComponent, UserSelectedEvent } from '../../core/ui/user-picker.component';
import { SectionPickerComponent, SectionSelectedEvent } from '../../core/ui/section-picker.component';
import { BatchPickerComponent, BatchSelectedEvent } from '../../core/ui/batch-picker.component';
import { ItemTypeaheadComponent, ItemSelectedEvent } from '../procurement/item-typeahead.component';
import { StockService } from './stock.service';
import { PostAdjustmentRequest } from './stock.models';

@Component({
  selector: 'orbix-stock-adjust',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ItemTypeaheadComponent, SectionPickerComponent, BatchPickerComponent, UserPickerComponent],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
        <a routerLink=".." class="text-decoration-none text-secondary">Stock</a> &rsaquo; Adjust
      </p>
      <h1 class="h3 fw-bold mb-1 text-dark">Stock adjustment</h1>
      <p class="text-secondary mb-0 small">Post a manual signed-quantity move — shrinkage, finds, write-offs, etc.</p>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i><span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="error.set(null)"></button>
      </div>
    }
    @if (info()) {
      <div class="alert alert-success d-flex align-items-center gap-2 py-2">
        <i class="bi bi-check-circle-fill"></i><span class="flex-grow-1">{{ info() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="info.set(null)"></button>
      </div>
    }

    @if (branchId() === null) {
      <div class="card border-0 shadow-sm">
        <div class="card-body p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-building"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">No active branch</h2>
          <p class="small text-secondary mb-0">Pick a branch in the top bar before posting an adjustment.</p>
        </div>
      </div>
    } @else {
      <div class="card border-0 shadow-sm">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">New adjustment</h2>
          <span class="badge text-bg-light text-secondary">Branch #{{ branchId() }}</span>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="onSubmit()" class="d-flex flex-column gap-3">
            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend"><i class="bi bi-sliders text-secondary"></i> Item &amp; quantity</legend>
              <div class="row g-2">
                <div class="col-md-6">
                  <orbix-item-typeahead
                    instanceId="adj-item"
                    (itemSelected)="onItemSelected($event)"
                    (itemCleared)="itemId.set(null); batchId.set(null)">
                  </orbix-item-typeahead>
                </div>
                <div class="col-md-3">
                  <label for="adj-qty" class="form-label small fw-semibold text-secondary">Signed qty</label>
                  <input id="adj-qty" class="form-control text-end" type="number" step="0.0001"
                         [(ngModel)]="qtyModel" (ngModelChange)="qty.set($event)" name="qty" required>
                  <small class="form-text text-secondary">+ inbound, − shrinkage / outbound</small>
                </div>
                <div class="col-md-3">
                  <label for="adj-unitCost" class="form-label small fw-semibold text-secondary">Unit cost <span class="text-muted">(in only)</span></label>
                  <input id="adj-unitCost" class="form-control text-end" type="number" step="0.0001"
                         [(ngModel)]="unitCostModel" (ngModelChange)="unitCost.set($event)" name="unitCost">
                </div>
              </div>
            </fieldset>

            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend"><i class="bi bi-clipboard-data text-secondary"></i> Context</legend>
              <div class="row g-2">
                <div class="col-12">
                  <label for="adj-reason" class="form-label small fw-semibold text-secondary">Reason</label>
                  <input id="adj-reason" class="form-control" type="text" [(ngModel)]="reasonModel"
                         (ngModelChange)="reason.set($event)" name="reason" required placeholder="e.g. weekly cycle count variance">
                </div>
                <div class="col-md-4">
                  <orbix-section-picker
                    instanceId="adj-section"
                    label="Section (opt)"
                    [required]="false"
                    [branchUid]="activeBranchUid()"
                    (sectionSelected)="onSectionSelected($event)"
                    (sectionCleared)="sectionId.set(null)">
                  </orbix-section-picker>
                </div>
                <div class="col-md-4">
                  <orbix-batch-picker
                    instanceId="adj-batch"
                    label="Batch (opt)"
                    [required]="false"
                    [itemId]="itemId()"
                    (batchSelected)="onBatchSelected($event)"
                    (batchCleared)="batchId.set(null)">
                  </orbix-batch-picker>
                </div>
                <div class="col-md-4">
                  <orbix-user-picker
                    instanceId="adj-authoriser"
                    label="Authoriser user"
                    [required]="false"
                    (userSelected)="onAuthoriserSelected($event)"
                    (userCleared)="authorisedByUserId.set(null)">
                  </orbix-user-picker>
                  <small class="form-text text-secondary">Required above threshold</small>
                </div>
              </div>
            </fieldset>

            @if (oversellPrompt()) {
              <div class="oversell-form border border-warning rounded-3 p-3 bg-warning-subtle">
                <p class="small fw-semibold text-warning-emphasis mb-2 d-flex align-items-center gap-2">
                  <i class="bi bi-shield-exclamation"></i>
                  Stock would go negative
                </p>
                <p class="small mb-2 text-dark">
                  This adjustment will drive on-hand stock below zero for this item at this branch.
                  Re-submit with <span class="font-monospace">allowOversell: true</span> to override —
                  the move is recorded with the OVERSELL hint for audit.
                </p>
                <div class="d-flex align-items-center gap-2 flex-wrap">
                  <button type="button"
                          class="btn btn-sm btn-warning d-inline-flex align-items-center gap-1"
                          [disabled]="busy()"
                          (click)="confirmOversell()">
                    @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                    @else { <i class="bi bi-shield-check"></i> }
                    Confirm OVERSELL &amp; re-submit
                  </button>
                  <button type="button" class="btn btn-sm btn-outline-secondary"
                          [disabled]="busy()" (click)="cancelOversell()">
                    Keep draft
                  </button>
                </div>
              </div>
            }

            <div class="d-flex gap-2 pt-2 border-top">
              <button type="submit" class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="busy()">
                @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                @else { <i class="bi bi-sliders"></i> }
                Post adjustment
              </button>
            </div>
          </form>
        </div>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }

    .form-fieldset {
      background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 10px; padding: 1rem 1.25rem 1.25rem;
    }
    .form-fieldset__legend {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #374151; padding: 0 0.5rem; width: auto; margin-bottom: 0.5rem;
    }
    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #ffe4e6; color: #be123c; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class AdjustComponent implements OnInit {
  private readonly stock = inject(StockService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);
  private readonly lookup = inject(CoreLookupService);

  protected readonly itemId = signal<string | null>(null);
  protected readonly qty = signal<number | null>(null);
  protected readonly unitCost = signal<number | null>(null);
  protected readonly reason = signal<string>('');
  protected readonly sectionId = signal<string | null>(null);
  protected readonly batchId = signal<string | null>(null);
  protected readonly authorisedByUserId = signal<string | null>(null);

  /** uid of the active branch — resolved once on init for section-picker. */
  protected readonly activeBranchUid = signal<string | null>(null);

  // ngModel mirrors for plain inputs
  protected qtyModel: number | null = null;
  protected unitCostModel: number | null = null;
  protected reasonModel = '';

  protected readonly busy = signal<boolean>(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);

  protected readonly oversellPrompt = signal<boolean>(false);
  private lastRequest: PostAdjustmentRequest | null = null;

  protected readonly canOverrideOversell = computed(() =>
    this.auth.hasPermission('STOCK.OVERSELL')
  );

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  ngOnInit(): void {
    // Resolve the active branch uid for the section-picker.
    const activeBranchId = this.branchId();
    if (activeBranchId !== null) {
      this.lookup.listBranches().subscribe({
        next: list => {
          const match = list.find(b => b.id === activeBranchId);
          if (match) this.activeBranchUid.set(match.uid);
        },
        error: () => { /* section-picker remains disabled, graceful degradation */ }
      });
    }
  }

  onItemSelected(evt: ItemSelectedEvent): void {
    this.itemId.set(evt.id);
    // Clear dependent batch when item changes
    this.batchId.set(null);
  }

  onSectionSelected(evt: SectionSelectedEvent): void { this.sectionId.set(evt.id); }
  onBatchSelected(evt: BatchSelectedEvent): void { this.batchId.set(evt.id); }
  onAuthoriserSelected(evt: UserSelectedEvent): void { this.authorisedByUserId.set(evt.id); }

  onSubmit(): void {
    const branchId = this.branchId();
    const itemId = this.itemId();
    const qty = this.qty();
    const reason = this.reason().trim();
    if (branchId === null || itemId === null || qty === null || !reason) {
      this.error.set('Item, signed qty and reason are required.');
      return;
    }
    const request: PostAdjustmentRequest = {
      itemId,
      branchId,
      qty,
      unitCost: this.unitCost(),
      reason,
      sectionId: this.sectionId(),
      batchId: this.batchId(),
      authorisedByUserId: this.authorisedByUserId(),
      allowOversell: false
    };
    this.submit(request, false);
  }

  confirmOversell(): void {
    if (!this.lastRequest) {
      this.oversellPrompt.set(false);
      return;
    }
    this.submit(this.lastRequest, true);
  }

  cancelOversell(): void {
    this.oversellPrompt.set(false);
    this.lastRequest = null;
  }

  private submit(request: PostAdjustmentRequest, allowOversell: boolean): void {
    this.error.set(null);
    this.info.set(null);
    this.busy.set(true);
    this.stock.postAdjustment(request, allowOversell).subscribe({
      next: move => {
        this.busy.set(false);
        this.info.set(allowOversell
          ? `Adjustment posted with OVERSELL as stock_move #${move.id}.`
          : `Adjustment posted as stock_move #${move.id}.`);
        this.oversellPrompt.set(false);
        this.lastRequest = null;
        this.qty.set(null);
        this.qtyModel = null;
        this.reason.set('');
        this.reasonModel = '';
      },
      error: err => {
        this.busy.set(false);
        this.handleError(err, request);
      }
    });
  }

  private handleError(err: unknown, attempted: PostAdjustmentRequest): void {
    if (this.isOversellError(err)) {
      this.lastRequest = attempted;
      if (this.canOverrideOversell()) {
        this.oversellPrompt.set(true);
        this.error.set(null);
      } else {
        this.oversellPrompt.set(false);
        this.error.set('Stock would go negative. Ask a supervisor to authorise (STOCK.OVERSELL).');
      }
      return;
    }
    this.oversellPrompt.set(false);
    this.showError(err);
  }

  private isOversellError(err: unknown): boolean {
    if (!(err instanceof HttpErrorResponse) || err.status !== 400) return false;
    const envelope = err.error as ApiResponse<unknown> | null;
    const msg = envelope?.message ?? '';
    return msg.includes('STOCK.OVERSELL');
  }

  private showError(err: unknown): void {
    if (err instanceof HttpErrorResponse) {
      const envelope = err.error as ApiResponse<unknown> | null;
      this.error.set(envelope?.message ?? `Request failed (${err.status})`);
    } else {
      this.error.set('Unexpected error');
    }
  }
}

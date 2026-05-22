import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { StockService } from './stock.service';

@Component({
  selector: 'orbix-stock-adjust',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
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
        <button type="button" class="btn-close btn-sm" (click)="error.set(null)"></button>
      </div>
    }
    @if (info()) {
      <div class="alert alert-success d-flex align-items-center gap-2 py-2">
        <i class="bi bi-check-circle-fill"></i><span class="flex-grow-1">{{ info() }}</span>
        <button type="button" class="btn-close btn-sm" (click)="info.set(null)"></button>
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
                  <label class="form-label small fw-semibold text-secondary">Item ID</label>
                  <input class="form-control" type="number" [(ngModel)]="itemIdModel"
                         (ngModelChange)="itemId.set($event)" name="itemId" required>
                </div>
                <div class="col-md-3">
                  <label class="form-label small fw-semibold text-secondary">Signed qty</label>
                  <input class="form-control text-end" type="number" step="0.0001"
                         [(ngModel)]="qtyModel" (ngModelChange)="qty.set($event)" name="qty" required>
                  <small class="form-text text-secondary">+ inbound, − shrinkage / outbound</small>
                </div>
                <div class="col-md-3">
                  <label class="form-label small fw-semibold text-secondary">Unit cost <span class="text-muted">(in only)</span></label>
                  <input class="form-control text-end" type="number" step="0.0001"
                         [(ngModel)]="unitCostModel" (ngModelChange)="unitCost.set($event)" name="unitCost">
                </div>
              </div>
            </fieldset>

            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend"><i class="bi bi-clipboard-data text-secondary"></i> Context</legend>
              <div class="row g-2">
                <div class="col-12">
                  <label class="form-label small fw-semibold text-secondary">Reason</label>
                  <input class="form-control" type="text" [(ngModel)]="reasonModel"
                         (ngModelChange)="reason.set($event)" name="reason" required placeholder="e.g. weekly cycle count variance">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Section ID <span class="text-muted">(opt)</span></label>
                  <input class="form-control" type="number" [(ngModel)]="sectionIdModel"
                         (ngModelChange)="sectionId.set($event)" name="sectionId">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Batch ID <span class="text-muted">(opt)</span></label>
                  <input class="form-control" type="number" [(ngModel)]="batchIdModel"
                         (ngModelChange)="batchId.set($event)" name="batchId">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Authoriser user ID</label>
                  <input class="form-control" type="number" [(ngModel)]="authoriserModel"
                         (ngModelChange)="authorisedByUserId.set($event)" name="authoriser">
                  <small class="form-text text-secondary">Required above threshold</small>
                </div>
                <div class="col-12">
                  <div class="form-check">
                    <input class="form-check-input" type="checkbox" id="oversell"
                           [(ngModel)]="allowOversellModel"
                           (ngModelChange)="allowOversell.set($event)" name="oversell">
                    <label class="form-check-label small" for="oversell">
                      Allow oversell <span class="text-muted">(requires STOCK.OVERSELL permission)</span>
                    </label>
                  </div>
                </div>
              </div>
            </fieldset>

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
export class AdjustComponent {
  private readonly stock = inject(StockService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  protected readonly itemId = signal<string | null>(null);
  protected readonly qty = signal<number | null>(null);
  protected readonly unitCost = signal<number | null>(null);
  protected readonly reason = signal<string>('');
  protected readonly sectionId = signal<string | null>(null);
  protected readonly batchId = signal<string | null>(null);
  protected readonly authorisedByUserId = signal<string | null>(null);
  protected readonly allowOversell = signal<boolean>(false);

  // ngModel mirrors
  protected itemIdModel: number | null = null;
  protected qtyModel: number | null = null;
  protected unitCostModel: number | null = null;
  protected reasonModel = '';
  protected sectionIdModel: number | null = null;
  protected batchIdModel: number | null = null;
  protected authoriserModel: number | null = null;
  protected allowOversellModel = false;

  protected readonly busy = signal<boolean>(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  onSubmit(): void {
    const branchId = this.branchId();
    const itemId = this.itemId();
    const qty = this.qty();
    const reason = this.reason().trim();
    if (branchId === null || itemId === null || qty === null || !reason) {
      this.error.set('Item, signed qty and reason are required.');
      return;
    }
    this.error.set(null);
    this.info.set(null);
    this.busy.set(true);
    this.stock.postAdjustment({
      itemId,
      branchId,
      qty,
      unitCost: this.unitCost(),
      reason,
      sectionId: this.sectionId(),
      batchId: this.batchId(),
      authorisedByUserId: this.authorisedByUserId(),
      allowOversell: this.allowOversell()
    }).subscribe({
      next: move => {
        this.busy.set(false);
        this.info.set(`Adjustment posted as stock_move #${move.id}.`);
        this.qty.set(null);
        this.qtyModel = null;
        this.reason.set('');
        this.reasonModel = '';
        this.allowOversell.set(false);
        this.allowOversellModel = false;
      },
      error: err => {
        this.busy.set(false);
        this.showError(err);
      }
    });
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

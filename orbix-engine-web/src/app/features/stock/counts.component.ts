import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { StockService } from './stock.service';
import { STOCK_COUNT_TYPES, StockCount, StockCountType } from './stock.models';

@Component({
  selector: 'orbix-stock-counts',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2 class="h3 mb-4">Stock counts</h2>
    @if (error()) { <div class="alert alert-danger py-2">{{ error() }}</div> }

    <div class="row g-4">
      <div class="col-12 col-lg-4">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Counts</div>
          <div class="list-group list-group-flush">
            @for (c of counts(); track c.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between"
                      [class.active]="selected()?.id === c.id" (click)="select(c)">
                <span>{{ c.number }} <small class="d-block text-muted">{{ c.countDate }} · {{ c.type }}</small></span>
                <span class="badge text-bg-secondary align-self-center">{{ c.status }}</span>
              </button>
            } @empty { <div class="list-group-item text-muted">No counts yet.</div> }
          </div>
        </div>

        <div class="card shadow-sm mt-3">
          <div class="card-header fw-semibold">New count</div>
          <div class="card-body">
            <form (ngSubmit)="create()" #f="ngForm">
              <div class="mb-2">
                <label class="form-label">Number</label>
                <input class="form-control" name="num" [(ngModel)]="newNumber" required>
              </div>
              <div class="mb-2">
                <label class="form-label">Count date</label>
                <input class="form-control" type="date" name="cd" [(ngModel)]="newDate" required>
              </div>
              <div class="mb-2">
                <label class="form-label">Type</label>
                <select class="form-select" name="ty" [(ngModel)]="newType" required>
                  @for (t of countTypes; track t) { <option [value]="t">{{ t }}</option> }
                </select>
              </div>
              <div class="mb-2">
                <label class="form-label">Item ids <small class="text-muted">(comma-separated)</small></label>
                <input class="form-control" name="ids" [(ngModel)]="newItemIds" required>
              </div>
              <button class="btn btn-primary w-100" [disabled]="busy() || f.invalid">Create count</button>
            </form>
          </div>
        </div>
      </div>

      <div class="col-12 col-lg-8">
        @if (selected(); as count) {
          <div class="card shadow-sm">
            <div class="card-header d-flex justify-content-between align-items-center">
              <span class="fw-semibold">{{ count.number }} — {{ count.status }}</span>
              <span class="d-flex gap-2">
                @if (count.status === 'DRAFT') {
                  <button class="btn btn-sm btn-outline-primary" (click)="act(count, 'start')"
                          [disabled]="busy()">Start</button>
                }
                @if (count.status === 'IN_PROGRESS') {
                  <button class="btn btn-sm btn-outline-primary" (click)="saveCounts(count)"
                          [disabled]="busy()">Save counts</button>
                  <button class="btn btn-sm btn-outline-warning" (click)="act(count, 'close')"
                          [disabled]="busy()">Close</button>
                }
                @if (count.status === 'CLOSED') {
                  <button class="btn btn-sm btn-warning" (click)="act(count, 'post')"
                          [disabled]="busy()">Post variances</button>
                }
              </span>
            </div>
            <div class="card-body">
              <table class="table table-sm align-middle">
                <thead>
                  <tr><th>Item</th><th class="text-end">System</th><th class="text-end">Counted</th>
                      <th class="text-end">Variance</th></tr>
                </thead>
                <tbody>
                  @for (l of count.lines; track l.id) {
                    <tr>
                      <td>{{ l.itemId }}</td>
                      <td class="text-end">{{ l.systemQty }}</td>
                      <td class="text-end" style="width: 140px">
                        @if (count.status === 'IN_PROGRESS') {
                          <input class="form-control form-control-sm text-end" type="number"
                                 [(ngModel)]="countedDraft[l.id]">
                        } @else { {{ l.countedQty ?? '—' }} }
                      </td>
                      <td class="text-end">{{ l.varianceQty ?? '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="text-muted">Select a count to record quantities and run its lifecycle.</div>
        }
      </div>
    </div>
  `
})
export class CountsComponent implements OnInit {
  private readonly stock = inject(StockService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly counts = signal<StockCount[]>([]);
  readonly selected = signal<StockCount | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  readonly countTypes = STOCK_COUNT_TYPES;
  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  newNumber = '';
  newDate = new Date().toISOString().slice(0, 10);
  newType: StockCountType = 'CYCLE';
  newItemIds = '';
  countedDraft: Record<number, number | null> = {};

  ngOnInit(): void {
    this.load();
  }

  select(count: StockCount): void {
    this.selected.set(count);
    this.countedDraft = {};
    for (const l of count.lines) {
      this.countedDraft[l.id] = l.countedQty;
    }
  }

  create(): void {
    const branchId = this.branchId();
    if (branchId === null) { this.error.set('No active branch.'); return; }
    const itemIds = this.newItemIds.split(',').map(s => Number(s.trim())).filter(n => Number.isFinite(n));
    this.run(this.stock.createCount({
      number: this.newNumber.trim(), branchId, countDate: this.newDate,
      type: this.newType, itemIds
    }), created => {
      this.newNumber = '';
      this.newItemIds = '';
      this.load();
      this.select(created);
    });
  }

  saveCounts(count: StockCount): void {
    const counts = count.lines
      .filter(l => this.countedDraft[l.id] != null)
      .map(l => ({ lineId: l.id, countedQty: Number(this.countedDraft[l.id]), note: l.note }));
    this.run(this.stock.recordCounts(count.id, { counts }), updated => this.refresh(updated));
  }

  act(count: StockCount, action: 'start' | 'close' | 'post'): void {
    const call =
      action === 'start' ? this.stock.startCount(count.id)
      : action === 'close' ? this.stock.closeCount(count.id)
      : this.stock.postCount(count.id);
    this.run(call, updated => this.refresh(updated));
  }

  private refresh(updated: StockCount): void {
    this.load();
    this.select(updated);
  }

  private run<T>(source: Observable<T>, onSuccess: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    source.subscribe({
      next: value => { this.busy.set(false); onSuccess(value); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private load(): void {
    this.stock.listCounts().subscribe({
      next: list => this.counts.set(list),
      error: err => this.showError(err)
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

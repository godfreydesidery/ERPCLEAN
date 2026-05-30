import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { UserPickerComponent, UserSelectedEvent } from '../../core/ui/user-picker.component';
import { ItemTypeaheadComponent, ItemSelectedEvent } from '../procurement/item-typeahead.component';
import { StockService } from './stock.service';
import { STOCK_COUNT_TYPES, StockCount, StockCountType } from './stock.models';

interface PickedItem {
  id: string;
  code: string;
  name: string;
}

@Component({
  selector: 'orbix-stock-counts',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, ItemTypeaheadComponent, UserPickerComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Stock</a> &rsaquo; Counts
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Stock counts</h1>
        <p class="text-secondary mb-0 small">{{ counts().length }} count{{ counts().length === 1 ? '' : 's' }} on file.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleForm()">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()"></i>
        {{ showForm() ? 'Close form' : 'New count' }}
      </button>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i><span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" (click)="error.set(null)"></button>
      </div>
    }

    @if (showForm()) {
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">New stock count</h2>
          <button class="btn-close btn-sm" (click)="toggleForm()"></button>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="create()" class="d-flex flex-column gap-3">
            <div class="row g-2">
              <div class="col-md-4">
                <label class="form-label small fw-semibold text-secondary">Number</label>
                <input class="form-control font-monospace" name="num" [(ngModel)]="newNumber" required placeholder="SC0001">
              </div>
              <div class="col-md-4">
                <label class="form-label small fw-semibold text-secondary">Count date</label>
                <input class="form-control" type="date" name="cd" [(ngModel)]="newDate" required>
              </div>
              <div class="col-md-4">
                <label class="form-label small fw-semibold text-secondary">Type</label>
                <select class="form-select" name="ty" [(ngModel)]="newType" required>
                  @for (t of countTypes; track t) { <option [value]="t">{{ t }}</option> }
                </select>
              </div>
            </div>

            <!-- Item picker with chip list -->
            <div>
              <label class="form-label small fw-semibold text-secondary">Items</label>
              <orbix-item-typeahead
                instanceId="count-item-add"
                (itemSelected)="addItem($event)"
                (itemCleared)="itemPickerKey.set(itemPickerKey() + 1)">
              </orbix-item-typeahead>
              @if (pickedItems().length > 0) {
                <div class="d-flex flex-wrap gap-1 mt-2" role="list" aria-label="Selected items">
                  @for (item of pickedItems(); track item.id) {
                    <span class="badge text-bg-light border d-inline-flex align-items-center gap-1" role="listitem">
                      <span class="font-monospace">{{ item.code }}</span>
                      <span class="text-secondary small">{{ item.name }}</span>
                      <button type="button" class="btn-close btn-sm ms-1"
                              style="font-size:0.6rem"
                              [attr.aria-label]="'Remove ' + item.name"
                              (click)="removeItem(item.id)">
                      </button>
                    </span>
                  }
                </div>
              } @else {
                <p class="small text-secondary mt-1 mb-0">Pick at least one item to count.</p>
              }
            </div>

            <div class="d-flex gap-2 pt-2 border-top">
              <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="busy() || !newNumber || pickedItems().length === 0">
                @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                @else { <i class="bi bi-clipboard-check"></i> }
                Create count
              </button>
              <button type="button" class="btn btn-outline-secondary" (click)="toggleForm()">Cancel</button>
            </div>
          </form>
        </div>
      </div>
    }

    <div class="row g-3 g-md-4">
      <div class="col-12 col-lg-5">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">Counts</h2>
            <span class="badge text-bg-light text-secondary">{{ counts().length }}</span>
          </div>
          @if (counts().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-clipboard-check"></i></div>
              <p class="small text-secondary mb-0">No counts yet.</p>
            </div>
          } @else {
            <ul class="list-unstyled mb-0 sc-list">
              @for (c of counts(); track c.id) {
                <li>
                  <button type="button" class="sc-row"
                          [class.is-active]="selected()?.id === c.id"
                          (click)="select(c)">
                    <div class="flex-grow-1 min-w-0">
                      <div class="d-flex align-items-center gap-2 mb-1">
                        <span class="badge text-bg-light border text-secondary font-monospace">{{ c.number }}</span>
                        <span class="status-badge status-badge--{{ c.status.toLowerCase() }}">
                          <span class="status-badge__dot"></span>{{ statusLabel(c.status) }}
                        </span>
                      </div>
                      <p class="small text-secondary mb-0">
                        {{ c.countDate | date:'mediumDate' }} · {{ c.type }} · {{ c.lines.length }} line{{ c.lines.length === 1 ? '' : 's' }}
                      </p>
                    </div>
                  </button>
                </li>
              }
            </ul>
          }
        </div>
      </div>

      <div class="col-12 col-lg-7">
        @if (selected(); as count) {
          <div class="card border-0 shadow-sm mb-3">
            <div class="card-body p-4">
              <div class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-3">
                <div>
                  <p class="small text-secondary mb-1">{{ count.countDate | date:'mediumDate' }} · {{ count.type }}</p>
                  <h2 class="h4 fw-bold mb-1 text-dark">{{ count.number }}</h2>
                  <span class="status-badge status-badge--{{ count.status.toLowerCase() }}">
                    <span class="status-badge__dot"></span>{{ statusLabel(count.status) }}
                  </span>
                </div>
                <div class="d-flex gap-2 flex-wrap">
                  @if (count.status === 'DRAFT') {
                    <button class="btn btn-sm btn-primary d-inline-flex align-items-center gap-1"
                            (click)="act(count, 'start')" [disabled]="busy()">
                      <i class="bi bi-play-fill"></i> Start
                    </button>
                  }
                  @if (count.status === 'IN_PROGRESS') {
                    <button class="btn btn-sm btn-primary d-inline-flex align-items-center gap-1"
                            (click)="saveCounts(count)" [disabled]="busy()">
                      <i class="bi bi-save"></i> Save counts
                    </button>
                    <button class="btn btn-sm btn-warning text-dark d-inline-flex align-items-center gap-1"
                            (click)="act(count, 'close')" [disabled]="busy()">
                      <i class="bi bi-lock"></i> Close
                    </button>
                  }
                  @if (count.status === 'CLOSED') {
                    <div class="d-flex flex-wrap align-items-end gap-2">
                      <div class="post-authoriser">
                        <orbix-user-picker
                          instanceId="count-post-authoriser"
                          label="Authoriser (if needed)"
                          [required]="false"
                          (userSelected)="onPostAuthoriserSelected($event)"
                          (userCleared)="postAuthoriserId = null">
                        </orbix-user-picker>
                      </div>
                      <button class="btn btn-sm btn-warning text-dark d-inline-flex align-items-center gap-1"
                              (click)="postVariances(count)" [disabled]="busy()">
                        <i class="bi bi-send-fill"></i> Post variances
                      </button>
                    </div>
                  }
                </div>
              </div>
            </div>
          </div>

          <div class="card border-0 shadow-sm overflow-hidden">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h3 class="h6 fw-bold mb-0 text-dark">Counted lines</h3>
              <span class="badge text-bg-light text-secondary">{{ count.lines.length }}</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr>
                    <th>Item</th>
                    <th class="text-end">System</th>
                    <th class="text-end">Counted</th>
                    <th class="text-end">Variance</th>
                  </tr>
                </thead>
                <tbody>
                  @for (l of count.lines; track l.id) {
                    <tr>
                      <td><span class="badge text-bg-light border text-secondary font-monospace">#{{ l.itemId }}</span></td>
                      <td class="text-end text-secondary">{{ l.systemQty }}</td>
                      <td class="text-end" style="width: 160px">
                        @if (count.status === 'IN_PROGRESS') {
                          <input class="form-control form-control-sm text-end" type="number"
                                 [(ngModel)]="countedDraft[l.id]">
                        } @else {
                          <span class="fw-semibold">{{ l.countedQty ?? '—' }}</span>
                        }
                      </td>
                      <td class="text-end fw-semibold"
                          [class.text-success]="(l.varianceQty ?? 0) > 0"
                          [class.text-danger]="(l.varianceQty ?? 0) < 0"
                          [class.text-secondary]="(l.varianceQty ?? 0) === 0">
                        @if (l.varianceQty != null && l.varianceQty > 0) { + }{{ l.varianceQty ?? '—' }}
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="card border-0 shadow-sm">
            <div class="card-body p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cursor"></i></div>
              <h2 class="h6 fw-bold mb-1 text-dark">Pick a count</h2>
              <p class="small text-secondary mb-0">Or start a new one to record quantities.</p>
            </div>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .min-w-0 { min-width: 0; }

    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }

    .sc-list { max-height: 70vh; overflow-y: auto; }
    .sc-row {
      width: 100%; display: flex; align-items: center; gap: 0.75rem;
      padding: 0.875rem 1rem; background: #fff; border: none;
      border-bottom: 1px solid #f3f4f6; text-align: left;
      transition: background 0.1s ease;
    }
    .sc-row:hover { background: #f8fafc; }
    .sc-row.is-active { background: #eef4ff; border-left: 3px solid #1d4ed8; padding-left: calc(1rem - 3px); }
    .sc-row:last-child { border-bottom: none; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.65rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.25rem 0.625rem; border-radius: 999px;
      font-size: 0.7rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 6px; height: 6px; border-radius: 50%; }
    .status-badge--draft       { background: #f3f4f6; color: #4b5563; }
    .status-badge--draft .status-badge__dot       { background: #9ca3af; }
    .status-badge--in_progress { background: #e0ecff; color: #1d4ed8; }
    .status-badge--in_progress .status-badge__dot { background: #3b82f6; }
    .status-badge--closed      { background: #fef3c7; color: #92400e; }
    .status-badge--closed .status-badge__dot      { background: #f59e0b; }
    .status-badge--posted      { background: #d1fae5; color: #047857; }
    .status-badge--posted .status-badge__dot      { background: #10b981; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #d1fae5; color: #047857; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .post-authoriser { min-width: 220px; }
  `]
})
export class CountsComponent implements OnInit {
  private readonly stock = inject(StockService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  protected readonly counts = signal<StockCount[]>([]);
  protected readonly selected = signal<StockCount | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showForm = signal(false);

  protected readonly countTypes = STOCK_COUNT_TYPES;
  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  protected newNumber = '';
  protected newDate = new Date().toISOString().slice(0, 10);
  protected newType: StockCountType = 'CYCLE';

  /** Items picked for the new count via the item typeahead chip-list. */
  protected readonly pickedItems = signal<PickedItem[]>([]);
  /** Increment to force the typeahead to reset after a pick. */
  protected readonly itemPickerKey = signal(0);

  protected countedDraft: Record<string, number | null> = {};
  protected postAuthoriserId: string | null = null;

  ngOnInit(): void { this.load(); }

  toggleForm(): void { this.showForm.update(v => !v); }

  statusLabel(status: string): string {
    return status === 'IN_PROGRESS' ? 'IN PROGRESS' : status;
  }

  addItem(evt: ItemSelectedEvent): void {
    const already = this.pickedItems().some(i => i.id === evt.id);
    if (!already) {
      this.pickedItems.update(list => [...list, { id: evt.id, code: evt.code, name: evt.name }]);
    }
    // Reset the typeahead so operator can pick another item.
    this.itemPickerKey.update(k => k + 1);
  }

  removeItem(id: string): void {
    this.pickedItems.update(list => list.filter(i => i.id !== id));
  }

  onPostAuthoriserSelected(evt: UserSelectedEvent): void {
    this.postAuthoriserId = evt.id;
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
    const items = this.pickedItems();
    if (items.length === 0) { this.error.set('Pick at least one item.'); return; }
    const itemIds = items.map(i => Number(i.id));
    this.run(this.stock.createCount({
      number: this.newNumber.trim(), branchId, countDate: this.newDate,
      type: this.newType, itemIds
    }), created => {
      this.newNumber = '';
      this.pickedItems.set([]);
      this.showForm.set(false);
      this.load();
      this.select(created);
    });
  }

  saveCounts(count: StockCount): void {
    const counts = count.lines
      .filter(l => this.countedDraft[l.id] != null)
      .map(l => ({ lineId: l.id, countedQty: Number(this.countedDraft[l.id]), note: l.note }));
    this.run(this.stock.recordCounts(count.uid, { counts }), updated => this.refresh(updated));
  }

  act(count: StockCount, action: 'start' | 'close'): void {
    const calls = {
      start: () => this.stock.startCount(count.uid),
      close: () => this.stock.closeCount(count.uid),
    };
    this.run(calls[action](), updated => this.refresh(updated));
  }

  postVariances(count: StockCount): void {
    this.run(
      this.stock.postCount(count.uid, { authorisedByUserId: this.postAuthoriserId }),
      updated => {
        this.postAuthoriserId = null;
        this.refresh(updated);
      }
    );
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
      const msg = envelope?.message ?? `Request failed (${err.status})`;
      if ((err.status === 400 || err.status === 403) && /authoriser/i.test(msg)) {
        this.error.set(
          'This count\'s variance exceeds the threshold — needs an authoriser ' +
          'holding STOCK.COUNT_APPROVE.'
        );
        return;
      }
      this.error.set(msg);
    } else {
      this.error.set('Unexpected error');
    }
  }
}

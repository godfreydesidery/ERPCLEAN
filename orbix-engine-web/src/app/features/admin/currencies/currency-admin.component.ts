import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { ConfirmService } from '../../../core/ui/confirm.service';
import { CurrencyAdminService } from './currency-admin.service';
import { Currency } from './currency-admin.models';

@Component({
  selector: 'orbix-currency-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
        <a routerLink=".." class="text-decoration-none text-secondary">Admin</a> &rsaquo; Currencies
      </p>
      <h1 class="h3 fw-bold mb-1 text-dark">Currencies</h1>
      <p class="text-secondary mb-0 small">{{ currencies().length }} currenc{{ currencies().length === 1 ? 'y' : 'ies' }} registered.</p>
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

    <div class="row g-3 g-md-4">
      <div class="col-12 col-lg-7">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3">
            <h2 class="h6 fw-bold mb-0 text-dark">Registered currencies</h2>
          </div>
          @if (currencies().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-currency-exchange"></i></div>
              <p class="small text-secondary mb-0">No currencies yet. Add one to get started.</p>
            </div>
          } @else {
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr>
                    <th>Code</th><th>Name</th><th>Symbol</th>
                    <th class="text-center">Digits</th><th>Status</th>
                    <th class="text-end actions-col"></th>
                  </tr>
                </thead>
                <tbody>
                  @for (currency of currencies(); track currency.code) {
                    <tr [class.table-active]="editingCode() === currency.code">
                      <td><span class="badge text-bg-light border text-secondary font-monospace">{{ currency.code }}</span></td>
                      <td class="fw-semibold text-dark">{{ currency.name }}</td>
                      <td class="font-monospace text-secondary">{{ currency.symbol || '—' }}</td>
                      <td class="text-center small text-secondary">{{ currency.minorUnitDigits }}</td>
                      <td>
                        <span class="status-badge status-badge--{{ currency.status.toLowerCase() }}">
                          <span class="status-badge__dot"></span>{{ currency.status }}
                        </span>
                      </td>
                      <td class="text-end actions-col">
                        <button class="btn btn-sm btn-outline-secondary d-inline-flex align-items-center gap-1 me-1"
                                (click)="startEdit(currency)" [disabled]="saving()">
                          <i class="bi bi-pencil"></i><span class="d-none d-md-inline">Edit</span>
                        </button>
                        @if (currency.status === 'ACTIVE') {
                          <button class="btn btn-sm btn-outline-secondary d-inline-flex align-items-center gap-1"
                                  (click)="setStatus(currency, false)" [disabled]="saving()">
                            <i class="bi bi-pause-circle"></i><span class="d-none d-md-inline">Disable</span>
                          </button>
                        } @else {
                          <button class="btn btn-sm btn-outline-primary d-inline-flex align-items-center gap-1"
                                  (click)="setStatus(currency, true)" [disabled]="saving()">
                            <i class="bi bi-play-circle"></i><span class="d-none d-md-inline">Enable</span>
                          </button>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      </div>

      <div class="col-12 col-lg-5">
        <div class="card border-0 shadow-sm">
          <div class="card-header bg-white border-bottom p-3">
            <h2 class="h6 fw-bold mb-0 text-dark">{{ editingCode() ? 'Edit ' + editingCode() : 'Add currency' }}</h2>
          </div>
          <div class="card-body p-3">
            <form (ngSubmit)="save()" #cf="ngForm" class="d-flex flex-column gap-3">
              <div>
                <label class="form-label small fw-semibold text-secondary">ISO code</label>
                <input class="form-control text-uppercase font-monospace" name="code" [(ngModel)]="form.code"
                       required pattern="[A-Za-z]{3}" maxlength="3" placeholder="USD" [disabled]="!!editingCode()">
                @if (editingCode()) {
                  <div class="form-text small">The ISO code can't be changed.</div>
                }
              </div>
              <div>
                <label class="form-label small fw-semibold text-secondary">Name</label>
                <input class="form-control" name="name" [(ngModel)]="form.name" required placeholder="US Dollar">
              </div>
              <div class="row g-2">
                <div class="col-7">
                  <label class="form-label small fw-semibold text-secondary">Symbol <span class="text-muted">(opt)</span></label>
                  <input class="form-control" name="symbol" [(ngModel)]="form.symbol" placeholder="$">
                </div>
                <div class="col-5">
                  <label class="form-label small fw-semibold text-secondary">Minor digits</label>
                  <input class="form-control text-end" type="number" name="digits" min="0" max="6"
                         [(ngModel)]="form.minorUnitDigits" required>
                </div>
              </div>
              <div class="d-flex gap-2 pt-2 border-top">
                @if (editingCode()) {
                  <button type="button" class="btn btn-outline-secondary" (click)="cancelEdit()" [disabled]="saving()">
                    Cancel
                  </button>
                }
                <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                        [disabled]="saving() || cf.invalid">
                  @if (saving()) { <span class="spinner-border spinner-border-sm"></span> }
                  @else { <i class="bi" [ngClass]="editingCode() ? 'bi-check-lg' : 'bi-plus-lg'"></i> }
                  {{ editingCode() ? 'Save changes' : 'Add currency' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }

    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.75rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }
    .simple-table .actions-col { width: 1%; white-space: nowrap; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.25rem 0.625rem; border-radius: 999px;
      font-size: 0.72rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 6px; height: 6px; border-radius: 50%; }
    .status-badge--active   { background: #d1fae5; color: #047857; }
    .status-badge--active .status-badge__dot   { background: #10b981; }
    .status-badge--disabled,
    .status-badge--inactive { background: #f3f4f6; color: #4b5563; }
    .status-badge--disabled .status-badge__dot,
    .status-badge--inactive .status-badge__dot { background: #9ca3af; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #fef3c7; color: #b45309; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class CurrencyAdminComponent implements OnInit, OnDestroy {
  private readonly api = inject(CurrencyAdminService);
  private readonly confirm = inject(ConfirmService);

  protected readonly currencies = signal<Currency[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly editingCode = signal<string | null>(null);

  protected form = { code: '', name: '', symbol: '', minorUnitDigits: 2 };

  private infoTimer: ReturnType<typeof setTimeout> | undefined;

  ngOnInit(): void { this.load(); }

  ngOnDestroy(): void { clearTimeout(this.infoTimer); }

  save(): void {
    const editing = this.editingCode();
    if (editing) {
      this.run(this.api.updateCurrency(editing, {
        name: this.form.name.trim(),
        symbol: this.form.symbol.trim() || null,
        minorUnitDigits: this.form.minorUnitDigits
      }), () => { this.resetForm(); this.load(); }, 'Currency updated.');
    } else {
      this.run(this.api.createCurrency({
        code: this.form.code.trim().toUpperCase(),
        name: this.form.name.trim(),
        symbol: this.form.symbol.trim() || null,
        minorUnitDigits: this.form.minorUnitDigits
      }), () => { this.resetForm(); this.load(); }, 'Currency added.');
    }
  }

  startEdit(currency: Currency): void {
    this.editingCode.set(currency.code);
    this.form = {
      code: currency.code,
      name: currency.name,
      symbol: currency.symbol ?? '',
      minorUnitDigits: currency.minorUnitDigits
    };
    this.error.set(null);
  }

  cancelEdit(): void { this.resetForm(); }

  async setStatus(currency: Currency, enable: boolean): Promise<void> {
    if (!enable) {
      const { ok } = await this.confirm.ask({
        title: 'Disable currency',
        message: `Disable ${currency.code} — ${currency.name}? It will be hidden from currency pickers until re-enabled.`,
        confirmText: 'Disable',
        variant: 'warning'
      });
      if (!ok) return;
    }
    const call = enable
      ? this.api.enableCurrency(currency.code)
      : this.api.disableCurrency(currency.code);
    this.run(call, () => this.load(), enable ? 'Currency enabled.' : 'Currency disabled.');
  }

  private resetForm(): void {
    this.editingCode.set(null);
    this.form = { code: '', name: '', symbol: '', minorUnitDigits: 2 };
  }

  private load(): void {
    this.api.listCurrencies().subscribe({
      next: currencies => this.currencies.set(currencies),
      error: err => this.showError(err)
    });
  }

  private run<T>(source: Observable<T>, onSuccess: (value: T) => void, successMsg?: string): void {
    this.saving.set(true);
    this.error.set(null);
    source.subscribe({
      next: value => {
        this.saving.set(false);
        onSuccess(value);
        if (successMsg) this.flashInfo(successMsg);
      },
      error: err => { this.saving.set(false); this.showError(err); }
    });
  }

  private flashInfo(message: string): void {
    this.info.set(message);
    clearTimeout(this.infoTimer);
    this.infoTimer = setTimeout(() => this.info.set(null), 4000);
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

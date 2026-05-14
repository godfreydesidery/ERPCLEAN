import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { CurrencyAdminService } from './currency-admin.service';
import { Currency } from './currency-admin.models';

@Component({
  selector: 'orbix-currency-admin',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2 class="h3 mb-4">Currencies</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <div class="row g-4">
      <div class="col-12 col-lg-7">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Registered currencies</div>
          <table class="table table-sm mb-0 align-middle">
            <thead>
              <tr><th>Code</th><th>Name</th><th>Symbol</th><th>Digits</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              @for (currency of currencies(); track currency.code) {
                <tr>
                  <td class="fw-semibold">{{ currency.code }}</td>
                  <td>{{ currency.name }}</td>
                  <td>{{ currency.symbol || '—' }}</td>
                  <td>{{ currency.minorUnitDigits }}</td>
                  <td>
                    @if (currency.status === 'ACTIVE') {
                      <span class="badge text-bg-success">ACTIVE</span>
                    } @else {
                      <span class="badge text-bg-secondary">{{ currency.status }}</span>
                    }
                  </td>
                  <td class="text-end">
                    @if (currency.status === 'ACTIVE') {
                      <button class="btn btn-sm btn-outline-secondary"
                              (click)="setStatus(currency, false)" [disabled]="saving()">Disable</button>
                    } @else {
                      <button class="btn btn-sm btn-outline-primary"
                              (click)="setStatus(currency, true)" [disabled]="saving()">Enable</button>
                    }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="text-muted">No currencies registered.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      <div class="col-12 col-lg-5">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Add currency</div>
          <div class="card-body">
            <form (ngSubmit)="create()" #cf="ngForm">
              <div class="mb-2">
                <label class="form-label">ISO code</label>
                <input class="form-control text-uppercase" name="code" [(ngModel)]="form.code"
                       required pattern="[A-Za-z]{3}" maxlength="3" placeholder="USD">
              </div>
              <div class="mb-2">
                <label class="form-label">Name</label>
                <input class="form-control" name="name" [(ngModel)]="form.name" required>
              </div>
              <div class="mb-2">
                <label class="form-label">Symbol</label>
                <input class="form-control" name="symbol" [(ngModel)]="form.symbol" placeholder="$">
              </div>
              <div class="mb-3">
                <label class="form-label">Minor unit digits</label>
                <input class="form-control" type="number" name="digits" min="0" max="6"
                       [(ngModel)]="form.minorUnitDigits" required>
              </div>
              <button class="btn btn-primary w-100" [disabled]="saving() || cf.invalid">Add currency</button>
            </form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class CurrencyAdminComponent implements OnInit {
  private readonly api = inject(CurrencyAdminService);

  readonly currencies = signal<Currency[]>([]);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  form = { code: '', name: '', symbol: '', minorUnitDigits: 2 };

  ngOnInit(): void {
    this.load();
  }

  create(): void {
    this.run(this.api.createCurrency({
      code: this.form.code.trim().toUpperCase(),
      name: this.form.name.trim(),
      symbol: this.form.symbol.trim() || null,
      minorUnitDigits: this.form.minorUnitDigits
    }), () => {
      this.form = { code: '', name: '', symbol: '', minorUnitDigits: 2 };
      this.load();
    });
  }

  setStatus(currency: Currency, enable: boolean): void {
    const call = enable
      ? this.api.enableCurrency(currency.code)
      : this.api.disableCurrency(currency.code);
    this.run(call, () => this.load());
  }

  private load(): void {
    this.api.listCurrencies().subscribe({
      next: currencies => this.currencies.set(currencies),
      error: err => this.showError(err)
    });
  }

  private run<T>(source: Observable<T>, onSuccess: (value: T) => void): void {
    this.saving.set(true);
    this.error.set(null);
    source.subscribe({
      next: value => {
        this.saving.set(false);
        onSuccess(value);
      },
      error: err => {
        this.saving.set(false);
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

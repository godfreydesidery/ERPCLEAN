import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { CurrencyAdminService } from './currency-admin.service';
import { Currency, FxRate } from './currency-admin.models';

@Component({
  selector: 'orbix-fx-rate-admin',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <h2 class="h3 mb-4">FX rates</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <div class="row g-4">
      <div class="col-12 col-lg-5">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Quote a rate</div>
          <div class="card-body">
            <form (ngSubmit)="quote()" #qf="ngForm">
              <div class="mb-2">
                <label class="form-label">From currency</label>
                <select class="form-select" name="from" [(ngModel)]="form.fromCurrency" required>
                  <option value="" disabled>Select…</option>
                  @for (c of currencies(); track c.code) {
                    <option [value]="c.code">{{ c.code }} — {{ c.name }}</option>
                  }
                </select>
              </div>
              <div class="mb-2">
                <label class="form-label">To currency</label>
                <select class="form-select" name="to" [(ngModel)]="form.toCurrency" required>
                  <option value="" disabled>Select…</option>
                  @for (c of currencies(); track c.code) {
                    <option [value]="c.code">{{ c.code }} — {{ c.name }}</option>
                  }
                </select>
              </div>
              <div class="mb-2">
                <label class="form-label">Rate</label>
                <input class="form-control" type="number" step="0.00000001" min="0"
                       name="rate" [(ngModel)]="form.rate" required>
              </div>
              <div class="mb-3">
                <label class="form-label">Effective from</label>
                <input class="form-control" type="datetime-local"
                       name="eff" [(ngModel)]="form.effectiveAt" required>
              </div>
              <button class="btn btn-primary w-100" [disabled]="saving() || qf.invalid">Quote rate</button>
            </form>
          </div>
        </div>
      </div>

      <div class="col-12 col-lg-7">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Rate history</div>
          <table class="table table-sm mb-0 align-middle">
            <thead>
              <tr><th>Pair</th><th class="text-end">Rate</th><th>Effective from</th></tr>
            </thead>
            <tbody>
              @for (rate of rates(); track rate.id) {
                <tr>
                  <td class="fw-semibold">{{ rate.fromCurrency }} → {{ rate.toCurrency }}</td>
                  <td class="text-end">{{ rate.rate }}</td>
                  <td>{{ rate.effectiveAt | date:'medium' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="3" class="text-muted">No rates quoted yet.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `
})
export class FxRateAdminComponent implements OnInit {
  private readonly api = inject(CurrencyAdminService);

  readonly currencies = signal<Currency[]>([]);
  readonly rates = signal<FxRate[]>([]);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  form = { fromCurrency: '', toCurrency: '', rate: null as number | null, effectiveAt: '' };

  ngOnInit(): void {
    this.api.listCurrencies().subscribe({
      next: currencies => this.currencies.set(currencies.filter(c => c.status === 'ACTIVE')),
      error: err => this.showError(err)
    });
    this.loadRates();
  }

  quote(): void {
    if (this.form.rate === null) return;
    this.run(this.api.quoteRate({
      fromCurrency: this.form.fromCurrency,
      toCurrency: this.form.toCurrency,
      rate: this.form.rate,
      // datetime-local has no zone — interpret as local time, send as ISO instant.
      effectiveAt: new Date(this.form.effectiveAt).toISOString()
    }), () => {
      this.form = { fromCurrency: '', toCurrency: '', rate: null, effectiveAt: '' };
      this.loadRates();
    });
  }

  private loadRates(): void {
    this.api.listRates().subscribe({
      next: rates => this.rates.set(rates),
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

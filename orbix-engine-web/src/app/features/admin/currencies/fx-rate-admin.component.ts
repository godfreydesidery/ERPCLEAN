import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { CurrencyAdminService } from './currency-admin.service';
import { Currency, FxRate } from './currency-admin.models';
import { SearchSelectComponent, SearchSelectOption } from '../../../core/ui/search-select.component';
import { PagerComponent } from '../../../core/ui/pager.component';

@Component({
  selector: 'orbix-fx-rate-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, SearchSelectComponent, PagerComponent],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
        <a routerLink=".." class="text-decoration-none text-secondary">Admin</a> &rsaquo; FX rates
      </p>
      <h1 class="h3 fw-bold mb-1 text-dark">FX rates</h1>
      <p class="text-secondary mb-0 small">{{ rates().length }} quote{{ rates().length === 1 ? '' : 's' }} on record.</p>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i><span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" (click)="error.set(null)"></button>
      </div>
    }

    <div class="row g-3 g-md-4">
      <div class="col-12 col-lg-5">
        <div class="card border-0 shadow-sm">
          <div class="card-header bg-white border-bottom p-3">
            <h2 class="h6 fw-bold mb-0 text-dark">Quote a rate</h2>
          </div>
          <div class="card-body p-3">
            <form (ngSubmit)="quote()" #qf="ngForm" class="d-flex flex-column gap-3">
              <div class="row g-2">
                <div class="col-6">
                  <label class="form-label small fw-semibold text-secondary">From</label>
                  <orbix-search-select [options]="currencyOptions()" [(ngModel)]="form.fromCurrency"
                                       name="from" required placeholder="Search…"/>
                </div>
                <div class="col-6">
                  <label class="form-label small fw-semibold text-secondary">To</label>
                  <orbix-search-select [options]="currencyOptions()" [(ngModel)]="form.toCurrency"
                                       name="to" required placeholder="Search…"/>
                </div>
              </div>
              <div>
                <label class="form-label small fw-semibold text-secondary">
                  Rate <span class="text-muted fw-normal">— 1 {{ form.fromCurrency || 'unit' }} = ? {{ form.toCurrency || 'unit' }}</span>
                </label>
                <input class="form-control text-end" type="number" step="0.00000001" min="0"
                       name="rate" [(ngModel)]="form.rate" required placeholder="1.0000">
              </div>
              <div>
                <label class="form-label small fw-semibold text-secondary">Effective from</label>
                <input class="form-control" type="datetime-local"
                       name="eff" [(ngModel)]="form.effectiveAt" required>
              </div>
              <div class="d-flex gap-2 pt-2 border-top">
                <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                        [disabled]="saving() || qf.invalid || !form.fromCurrency || !form.toCurrency">
                  @if (saving()) { <span class="spinner-border spinner-border-sm"></span> }
                  @else { <i class="bi bi-graph-up"></i> }
                  Quote rate
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>

      <div class="col-12 col-lg-7">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">Rate history</h2>
            <span class="badge text-bg-light text-secondary">{{ rates().length }}</span>
          </div>
          @if (rates().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-graph-up"></i></div>
              <p class="small text-secondary mb-0">No rates quoted yet.</p>
            </div>
          } @else {
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr><th>Pair</th><th class="text-end">Rate</th><th>Effective from</th></tr>
                </thead>
                <tbody>
                  @for (rate of pagedRates(); track rate.id) {
                    <tr>
                      <td>
                        <span class="badge text-bg-light border text-secondary font-monospace">{{ rate.fromCurrency }}</span>
                        <i class="bi bi-arrow-right mx-1 text-secondary"></i>
                        <span class="badge text-bg-light border text-secondary font-monospace">{{ rate.toCurrency }}</span>
                      </td>
                      <td class="text-end fw-semibold">{{ rate.rate }}</td>
                      <td class="small text-secondary">{{ rate.effectiveAt | date:'medium' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            @if (totalRatePages() > 1) {
              <div class="card-footer bg-white border-top p-3">
                <orbix-pager [page]="ratesPage()" [totalPages]="totalRatePages()"
                             [totalElements]="rates().length" [pageSize]="ratesPageSize"
                             (pageChange)="ratesPage.set($event)"/>
              </div>
            }
          }
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

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #ede9fe; color: #6d28d9; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class FxRateAdminComponent implements OnInit {
  private readonly api = inject(CurrencyAdminService);

  protected readonly currencies = signal<Currency[]>([]);
  protected readonly currencyOptions = computed<SearchSelectOption[]>(
    () => this.currencies().map(c => ({ id: c.code, label: `${c.code} — ${c.name}` })));
  protected readonly rates = signal<FxRate[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly ratesPageSize = 15;
  protected readonly ratesPage = signal(0);
  protected readonly totalRatePages = computed(() =>
    Math.max(1, Math.ceil(this.rates().length / this.ratesPageSize)));
  protected readonly pagedRates = computed<FxRate[]>(() => {
    const page = Math.min(this.ratesPage(), this.totalRatePages() - 1);
    const start = page * this.ratesPageSize;
    return this.rates().slice(start, start + this.ratesPageSize);
  });

  protected form = { fromCurrency: '', toCurrency: '', rate: null as number | null, effectiveAt: '' };

  ngOnInit(): void {
    this.api.listCurrencies().subscribe({
      next: currencies => this.currencies.set(currencies.filter(c => c.status === 'ACTIVE')),
      error: err => this.showError(err)
    });
    this.loadRates();
  }

  quote(): void {
    if (this.form.rate === null || !this.form.fromCurrency || !this.form.toCurrency) return;
    this.run(this.api.quoteRate({
      fromCurrency: this.form.fromCurrency,
      toCurrency: this.form.toCurrency,
      rate: this.form.rate,
      effectiveAt: new Date(this.form.effectiveAt).toISOString()
    }), () => {
      this.form = { fromCurrency: '', toCurrency: '', rate: null, effectiveAt: '' };
      this.loadRates();
    });
  }

  private loadRates(): void {
    this.api.listRates().subscribe({
      next: rates => { this.rates.set(rates); this.ratesPage.set(0); },
      error: err => this.showError(err)
    });
  }

  private run<T>(source: Observable<T>, onSuccess: (value: T) => void): void {
    this.saving.set(true);
    this.error.set(null);
    source.subscribe({
      next: value => { this.saving.set(false); onSuccess(value); },
      error: err => { this.saving.set(false); this.showError(err); }
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

import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { PartyService } from './party.service';
import { PartyDetailsFormComponent } from './party-details-form.component';
import { Customer, PartyDetails, PartyResponse, blankPartyDetails } from './party.models';

@Component({
  selector: 'orbix-customers',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DecimalPipe, PartyDetailsFormComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Parties</a> &rsaquo; Customers
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Customers</h1>
        <p class="text-secondary mb-0 small">{{ customers().length }} customer{{ customers().length === 1 ? '' : 's' }} on file.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleForm()">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()"></i>
        {{ showForm() ? 'Close form' : 'New customer' }}
      </button>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="error.set(null)"></button>
      </div>
    }

    <!-- Create form (collapsible) -->
    @if (showForm()) {
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">New customer</h2>
          <button class="btn-close btn-sm" (click)="toggleForm()" aria-label="Close"></button>
        </div>
        <div class="card-body p-3">
          @if (tinMatch(); as match) {
            <div class="alert alert-info d-flex align-items-start gap-2 py-2 mb-3">
              <i class="bi bi-info-circle-fill mt-1"></i>
              <div class="flex-grow-1 small">
                A party with this TIN already exists —
                <strong>{{ match.code }} · {{ match.name }}</strong>.
                Creating will attach the customer role to that existing party.
              </div>
            </div>
          }
          <form (ngSubmit)="create()" #f="ngForm" class="d-flex flex-column gap-3">
            <div class="row g-2">
              <div class="col-md-4">
                <label class="form-label small fw-semibold text-secondary">Party code</label>
                <input class="form-control font-monospace" name="code" [(ngModel)]="code" required
                       placeholder="e.g. CUS0042">
              </div>
            </div>

            <orbix-party-details-form [details]="partyDetails" (tinBlur)="checkTin($event)" />

            <fieldset class="role-fieldset">
              <legend class="role-fieldset__legend">
                <i class="bi bi-credit-card text-secondary"></i> Customer terms
              </legend>
              <div class="row g-2">
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Credit limit</label>
                  <input class="form-control text-end" type="number" name="cl" [(ngModel)]="creditLimitAmount">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Credit terms <span class="text-muted">(days)</span></label>
                  <input class="form-control text-end" type="number" name="ct" [(ngModel)]="creditTermsDays">
                </div>
                <div class="col-md-4 d-flex align-items-end">
                  <div class="form-check pb-2">
                    <input class="form-check-input" type="checkbox" id="texempt" name="texempt"
                           [(ngModel)]="taxExempt">
                    <label class="form-check-label small" for="texempt">Tax exempt</label>
                  </div>
                </div>
              </div>
            </fieldset>

            <div class="d-flex gap-2 pt-2 border-top">
              <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="busy() || f.invalid">
                @if (busy()) {
                  <span class="spinner-border spinner-border-sm"></span>
                } @else {
                  <i class="bi bi-plus-lg"></i>
                }
                Create customer
              </button>
              <button type="button" class="btn btn-outline-secondary" (click)="toggleForm()">Cancel</button>
            </div>
          </form>
        </div>
      </div>
    }

    <!-- Toolbar -->
    <div class="card border-0 shadow-sm mb-3">
      <div class="card-body p-3 d-flex flex-wrap align-items-center gap-3">
        <div class="search-box flex-grow-1">
          <i class="bi bi-search"></i>
          <input type="search" class="form-control" placeholder="Search by code, name or TIN"
                 [(ngModel)]="searchTerm" (ngModelChange)="searchSignal.set(searchTerm)">
        </div>
        <div class="status-pills d-flex gap-1 flex-wrap">
          @for (opt of statusOptions; track opt.value) {
            <button type="button" class="status-pill"
                    [class.is-active]="statusFilter() === opt.value"
                    (click)="statusFilter.set(opt.value)">
              {{ opt.label }}
            </button>
          }
        </div>
      </div>
    </div>

    <!-- List -->
    <div class="card border-0 shadow-sm overflow-hidden">
      @if (filtered().length === 0) {
        <div class="p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-person-circle"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">No customers match</h2>
          <p class="small text-secondary mb-0">
            @if (searchTerm) {
              Try a different code, name or TIN.
            } @else {
              Add your first customer to start invoicing.
            }
          </p>
        </div>
      } @else {
        <div class="table-responsive d-none d-md-block">
          <table class="table table-hover align-middle mb-0 simple-table">
            <thead>
              <tr>
                <th>Code</th><th>Name</th><th>TIN</th>
                <th class="text-end">Credit limit</th><th>Terms</th><th>Status</th>
                <th class="text-end actions-col"></th>
              </tr>
            </thead>
            <tbody>
              @for (customer of filtered(); track customer.partyId) {
                <tr>
                  <td><span class="badge text-bg-light border text-secondary font-monospace">{{ customer.party.code }}</span></td>
                  <td>
                    <div class="fw-semibold text-dark">{{ customer.party.name }}</div>
                    @if (customer.walkIn) {
                      <span class="badge text-bg-primary-subtle text-primary small">WALK-IN</span>
                    }
                    @if (customer.taxExempt) {
                      <span class="badge text-bg-warning-subtle text-warning small ms-1">TAX-EXEMPT</span>
                    }
                  </td>
                  <td class="font-monospace small text-secondary">{{ customer.party.tin ?? '—' }}</td>
                  <td class="text-end fw-semibold">{{ customer.creditLimitAmount | number:'1.2-2' }}</td>
                  <td class="small text-secondary">{{ customer.creditTermsDays }}d</td>
                  <td>
                    <span class="status-badge status-badge--{{ customer.party.status.toLowerCase() }}">
                      <span class="status-badge__dot"></span>{{ customer.party.status }}
                    </span>
                  </td>
                  <td class="text-end actions-col">
                    @if (customer.party.status === 'ACTIVE' && !customer.walkIn) {
                      <button class="btn btn-sm btn-outline-danger" (click)="deactivate(customer)"
                              [disabled]="busy()" title="Deactivate">
                        <i class="bi bi-pause-circle"></i>
                      </button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <!-- Mobile cards -->
        <ul class="list-unstyled mb-0 d-md-none">
          @for (customer of filtered(); track customer.partyId) {
            <li class="party-card">
              <div class="d-flex justify-content-between align-items-start gap-2 mb-1">
                <div class="flex-grow-1">
                  <span class="badge text-bg-light border text-secondary font-monospace mb-1">{{ customer.party.code }}</span>
                  <p class="fw-semibold text-dark mb-0">{{ customer.party.name }}</p>
                  @if (customer.party.tin) {
                    <p class="small text-secondary mb-0 font-monospace">TIN {{ customer.party.tin }}</p>
                  }
                </div>
                <span class="status-badge status-badge--{{ customer.party.status.toLowerCase() }}">
                  <span class="status-badge__dot"></span>{{ customer.party.status }}
                </span>
              </div>
              <div class="d-flex justify-content-between small text-secondary mt-2">
                <span>Credit {{ customer.creditLimitAmount | number:'1.2-2' }}</span>
                <span>{{ customer.creditTermsDays }} days</span>
              </div>
              @if (customer.party.status === 'ACTIVE' && !customer.walkIn) {
                <button class="btn btn-sm btn-outline-danger w-100 mt-2" (click)="deactivate(customer)"
                        [disabled]="busy()">
                  <i class="bi bi-pause-circle me-1"></i> Deactivate
                </button>
              }
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }

    .search-box { position: relative; min-width: 220px; }
    .search-box i { position: absolute; left: 0.875rem; top: 50%; transform: translateY(-50%); color: #9ca3af; pointer-events: none; }
    .search-box .form-control { padding-left: 2.4rem; border: 1px solid #e5e7eb; }
    .search-box .form-control:focus { border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12); }

    .status-pill {
      padding: 0.4rem 0.85rem; font-size: 0.85rem; font-weight: 500;
      border: 1px solid #e5e7eb; border-radius: 999px; background: #fff; color: #6b7280;
      transition: all 0.15s ease;
    }
    .status-pill:hover { border-color: #cbd5e1; color: #1f2937; }
    .status-pill.is-active { background: #0d2a5b; border-color: #0d2a5b; color: #fff; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.875rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
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
    .status-badge--inactive { background: #fef3c7; color: #92400e; }
    .status-badge--inactive .status-badge__dot { background: #f59e0b; }
    .status-badge--archived { background: #f3f4f6; color: #4b5563; }
    .status-badge--archived .status-badge__dot { background: #9ca3af; }

    .text-bg-primary-subtle { background: #e0ecff; color: #1d4ed8; }
    .text-bg-warning-subtle { background: #fef3c7; color: #92400e; }

    .party-card { padding: 1rem; border-bottom: 1px solid #f3f4f6; }
    .party-card:last-child { border-bottom: none; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #e0ecff; color: #1d4ed8; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .role-fieldset {
      background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 10px; padding: 1rem 1.25rem 1.25rem;
    }
    .role-fieldset__legend {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #374151; padding: 0 0.5rem; width: auto; margin-bottom: 0.5rem;
    }

    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }

    @media (max-width: 575.98px) {
      .search-box { min-width: 100%; }
      .status-pills { width: 100%; overflow-x: auto; flex-wrap: nowrap; }
      .status-pill { flex-shrink: 0; }
    }
  `]
})
export class CustomersComponent implements OnInit {
  private readonly party = inject(PartyService);

  protected readonly customers = signal<Customer[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly tinMatch = signal<PartyResponse | null>(null);
  protected readonly showForm = signal(false);

  protected readonly statusFilter = signal<'ACTIVE' | 'INACTIVE' | 'ARCHIVED' | null>(null);
  protected readonly searchSignal = signal('');
  protected searchTerm = '';

  protected readonly statusOptions = [
    { label: 'All',      value: null },
    { label: 'Active',   value: 'ACTIVE' as const },
    { label: 'Inactive', value: 'INACTIVE' as const },
    { label: 'Archived', value: 'ARCHIVED' as const },
  ];

  protected readonly filtered = computed(() => {
    const status = this.statusFilter();
    const q = this.searchSignal().trim().toLowerCase();
    return this.customers().filter(c => {
      if (status && c.party.status !== status) return false;
      if (!q) return true;
      return c.party.code.toLowerCase().includes(q)
          || c.party.name.toLowerCase().includes(q)
          || (c.party.tin?.toLowerCase().includes(q) ?? false);
    });
  });

  protected code = '';
  protected partyDetails: PartyDetails = blankPartyDetails();
  protected creditLimitAmount = 0;
  protected creditTermsDays = 0;
  protected taxExempt = false;

  ngOnInit(): void {
    this.load();
  }

  toggleForm(): void {
    this.showForm.update(v => !v);
    if (!this.showForm()) this.reset();
  }

  checkTin(tin: string): void {
    this.party.findByTin(tin).subscribe(match => this.tinMatch.set(match));
  }

  create(): void {
    this.busy.set(true);
    this.error.set(null);
    this.party.createCustomer({
      code: this.code.trim(),
      party: this.partyDetails,
      creditLimitAmount: Number(this.creditLimitAmount),
      creditTermsDays: Number(this.creditTermsDays),
      priceListId: null,
      defaultSalesAgentId: null,
      defaultBranchId: null,
      taxExempt: this.taxExempt
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.reset();
        this.showForm.set(false);
        this.load();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  deactivate(customer: Customer): void {
    this.busy.set(true);
    this.party.deactivateCustomer(customer.partyId).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private reset(): void {
    this.code = '';
    this.partyDetails = blankPartyDetails();
    this.creditLimitAmount = 0;
    this.creditTermsDays = 0;
    this.taxExempt = false;
    this.tinMatch.set(null);
  }

  private load(): void {
    this.party.listCustomers().subscribe({
      next: list => this.customers.set(list),
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

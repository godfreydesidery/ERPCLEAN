import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { PartyService } from './party.service';
import { PartyDetailsFormComponent } from './party-details-form.component';
import { PartyDetails, PartyResponse, Supplier, blankPartyDetails } from './party.models';

@Component({
  selector: 'orbix-suppliers',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, PartyDetailsFormComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Parties</a> &rsaquo; Suppliers
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Suppliers</h1>
        <p class="text-secondary mb-0 small">{{ suppliers().length }} supplier{{ suppliers().length === 1 ? '' : 's' }} on file.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleForm()">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()"></i>
        {{ showForm() ? 'Close form' : 'New supplier' }}
      </button>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="error.set(null)"></button>
      </div>
    }

    @if (showForm()) {
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">New supplier</h2>
          <button class="btn-close btn-sm" (click)="toggleForm()" aria-label="Close"></button>
        </div>
        <div class="card-body p-3">
          @if (tinMatch(); as match) {
            <div class="alert alert-info d-flex align-items-start gap-2 py-2 mb-3">
              <i class="bi bi-info-circle-fill mt-1"></i>
              <div class="flex-grow-1 small">
                A party with this TIN already exists —
                <strong>{{ match.code }} · {{ match.name }}</strong>.
                Creating will attach the supplier role to that existing party.
              </div>
            </div>
          }
          <form (ngSubmit)="create()" #f="ngForm" class="d-flex flex-column gap-3">
            <div class="row g-2">
              <div class="col-md-4">
                <label class="form-label small fw-semibold text-secondary">Party code</label>
                <input class="form-control font-monospace" name="code" [(ngModel)]="code" required
                       placeholder="e.g. SUP0042">
              </div>
            </div>

            <orbix-party-details-form [details]="partyDetails" (tinBlur)="checkTin($event)" />

            <fieldset class="role-fieldset">
              <legend class="role-fieldset__legend">
                <i class="bi bi-cash-coin text-secondary"></i> Trading terms
              </legend>
              <div class="row g-2">
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Payment terms <span class="text-muted">(days)</span></label>
                  <input class="form-control text-end" type="number" name="pt" [(ngModel)]="paymentTermsDays">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Default currency</label>
                  <input class="form-control text-uppercase font-monospace" maxlength="3" name="ccy"
                         [(ngModel)]="defaultCurrencyCode" placeholder="UGX">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Lead time <span class="text-muted">(days)</span></label>
                  <input class="form-control text-end" type="number" name="lt" [(ngModel)]="leadTimeDays">
                </div>
              </div>
            </fieldset>

            <fieldset class="role-fieldset">
              <legend class="role-fieldset__legend">
                <i class="bi bi-bank text-secondary"></i> Bank details <span class="text-muted text-lowercase">(optional)</span>
              </legend>
              <div class="row g-2">
                <div class="col-md-6">
                  <label class="form-label small fw-semibold text-secondary">Bank name</label>
                  <input class="form-control" name="bn" [(ngModel)]="bankName">
                </div>
                <div class="col-md-6">
                  <label class="form-label small fw-semibold text-secondary">Account number</label>
                  <input class="form-control font-monospace" name="ba" [(ngModel)]="bankAccountNo">
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
                Create supplier
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
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-truck"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">No suppliers match</h2>
          <p class="small text-secondary mb-0">
            @if (searchTerm) {
              Try a different code, name or TIN.
            } @else {
              Add your first supplier to start receiving stock.
            }
          </p>
        </div>
      } @else {
        <div class="table-responsive d-none d-md-block">
          <table class="table table-hover align-middle mb-0 simple-table">
            <thead>
              <tr>
                <th>Code</th><th>Name</th><th>TIN</th>
                <th>Terms</th><th>Lead</th><th>Status</th>
                <th class="text-end actions-col"></th>
              </tr>
            </thead>
            <tbody>
              @for (supplier of filtered(); track supplier.partyId) {
                <tr>
                  <td><span class="badge text-bg-light border text-secondary font-monospace">{{ supplier.party.code }}</span></td>
                  <td>
                    <div class="fw-semibold text-dark">{{ supplier.party.name }}</div>
                    @if (supplier.defaultCurrencyCode && supplier.defaultCurrencyCode !== 'UGX') {
                      <span class="small text-secondary">{{ supplier.defaultCurrencyCode }}</span>
                    }
                  </td>
                  <td class="font-monospace small text-secondary">{{ supplier.party.tin ?? '—' }}</td>
                  <td class="small text-secondary">{{ supplier.paymentTermsDays }}d</td>
                  <td class="small text-secondary">{{ supplier.leadTimeDays != null ? supplier.leadTimeDays + 'd' : '—' }}</td>
                  <td>
                    <span class="status-badge status-badge--{{ supplier.party.status.toLowerCase() }}">
                      <span class="status-badge__dot"></span>{{ supplier.party.status }}
                    </span>
                  </td>
                  <td class="text-end actions-col">
                    @if (supplier.party.status === 'ACTIVE') {
                      <button class="btn btn-sm btn-outline-danger" (click)="deactivate(supplier)"
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

        <ul class="list-unstyled mb-0 d-md-none">
          @for (supplier of filtered(); track supplier.partyId) {
            <li class="party-card">
              <div class="d-flex justify-content-between align-items-start gap-2 mb-1">
                <div class="flex-grow-1">
                  <span class="badge text-bg-light border text-secondary font-monospace mb-1">{{ supplier.party.code }}</span>
                  <p class="fw-semibold text-dark mb-0">{{ supplier.party.name }}</p>
                  @if (supplier.party.tin) {
                    <p class="small text-secondary mb-0 font-monospace">TIN {{ supplier.party.tin }}</p>
                  }
                </div>
                <span class="status-badge status-badge--{{ supplier.party.status.toLowerCase() }}">
                  <span class="status-badge__dot"></span>{{ supplier.party.status }}
                </span>
              </div>
              <div class="d-flex justify-content-between small text-secondary mt-2">
                <span>{{ supplier.paymentTermsDays }} day terms</span>
                <span>Lead {{ supplier.leadTimeDays ?? '—' }}{{ supplier.leadTimeDays != null ? 'd' : '' }}</span>
              </div>
              @if (supplier.party.status === 'ACTIVE') {
                <button class="btn btn-sm btn-outline-danger w-100 mt-2" (click)="deactivate(supplier)"
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

    .party-card { padding: 1rem; border-bottom: 1px solid #f3f4f6; }
    .party-card:last-child { border-bottom: none; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #d1fae5; color: #047857; font-size: 1.75rem;
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
export class SuppliersComponent implements OnInit {
  private readonly party = inject(PartyService);

  protected readonly suppliers = signal<Supplier[]>([]);
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
    return this.suppliers().filter(s => {
      if (status && s.party.status !== status) return false;
      if (!q) return true;
      return s.party.code.toLowerCase().includes(q)
          || s.party.name.toLowerCase().includes(q)
          || (s.party.tin?.toLowerCase().includes(q) ?? false);
    });
  });

  protected code = '';
  protected partyDetails: PartyDetails = blankPartyDetails();
  protected paymentTermsDays = 0;
  protected defaultCurrencyCode: string | null = null;
  protected leadTimeDays: number | null = null;
  protected bankName: string | null = null;
  protected bankAccountNo: string | null = null;

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
    this.party.createSupplier({
      code: this.code.trim(),
      party: this.partyDetails,
      paymentTermsDays: Number(this.paymentTermsDays),
      creditLimitAmount: 0,
      defaultCurrencyCode: this.defaultCurrencyCode,
      bankName: this.bankName,
      bankAccountNo: this.bankAccountNo,
      leadTimeDays: this.leadTimeDays == null ? null : Number(this.leadTimeDays)
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

  deactivate(supplier: Supplier): void {
    this.busy.set(true);
    this.party.deactivateSupplier(supplier.partyId).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private reset(): void {
    this.code = '';
    this.partyDetails = blankPartyDetails();
    this.paymentTermsDays = 0;
    this.defaultCurrencyCode = null;
    this.leadTimeDays = null;
    this.bankName = null;
    this.bankAccountNo = null;
    this.tinMatch.set(null);
  }

  private load(): void {
    this.party.listSuppliers().subscribe({
      next: list => this.suppliers.set(list),
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

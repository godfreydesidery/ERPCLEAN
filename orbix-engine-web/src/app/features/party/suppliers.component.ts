import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { Currency, CurrencyService } from '../../core/currency/currency.service';
import { SearchSelectComponent, SearchSelectOption } from '../../core/ui/search-select.component';
import { PagerComponent } from '../../core/ui/pager.component';
import { PartyService } from './party.service';
import { PartyDetailsFormComponent } from './party-details-form.component';
import {
  PartyDetails,
  PartyResponse,
  Supplier,
  UpdateSupplierRequest,
  blankPartyDetails
} from './party.models';

@Component({
  selector: 'orbix-suppliers',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, PartyDetailsFormComponent, SearchSelectComponent, PagerComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Parties</a> &rsaquo; Suppliers
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Suppliers</h1>
        <p class="text-secondary mb-0 small">{{ total() }} supplier{{ total() === 1 ? '' : 's' }} on file.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleForm()"
              [title]="showForm() ? 'Close the form without saving' : 'Open the form to register a new supplier'">
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
          <h2 class="h6 fw-bold mb-0 text-dark">
            {{ editing() ? 'Edit supplier' : 'New supplier' }}
          </h2>
          <button class="btn-close btn-sm" (click)="toggleForm()" aria-label="Close"
                  title="Close the form without saving"></button>
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
          <form (ngSubmit)="submit()" #f="ngForm" class="d-flex flex-column gap-3">
            @if (editing()) {
              <div class="d-flex flex-wrap align-items-center gap-2">
                <span class="text-secondary small">Editing</span>
                <span class="badge text-bg-light border text-secondary font-monospace">{{ editing()!.party.code }}</span>
              </div>

              <orbix-party-details-form [details]="partyDetails" />
            } @else {
              <div class="party-mode-toggle">
                <button type="button" class="party-mode-toggle__btn"
                        [class.is-active]="partyMode() === 'pick'"
                        (click)="setPartyMode('pick')"
                        title="Promote an existing party (e.g. a customer or employee already on file) into the supplier role.">
                  <i class="bi bi-person-check me-1"></i> Pick existing party
                </button>
                <button type="button" class="party-mode-toggle__btn"
                        [class.is-active]="partyMode() === 'create'"
                        (click)="setPartyMode('create')"
                        title="Register a brand-new party and assign the supplier role in one step.">
                  <i class="bi bi-person-plus me-1"></i> Create new party
                </button>
              </div>

              @if (partyMode() === 'pick') {
                <div class="row g-2">
                  <div class="col-md-12">
                    <label class="form-label small fw-semibold text-secondary">Party</label>
                    <orbix-search-select name="party" [options]="partyOptions()"
                                         [(ngModel)]="partyId" placeholder="Pick a customer / employee / agent…" required>
                    </orbix-search-select>
                    <p class="form-text small mb-0">Promotes the chosen party into the supplier role.</p>
                  </div>
                </div>
              } @else {
                <p class="form-text small mb-0">
                  <i class="bi bi-info-circle me-1"></i>
                  Party code is auto-generated from the <span class="font-monospace">SUP</span> sequence on save.
                </p>

                <orbix-party-details-form [details]="partyDetails" (tinBlur)="checkTin($event)" />
              }
            }

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
                  <orbix-search-select name="ccy" [options]="currencyOptions()"
                                       [(ngModel)]="defaultCurrencyCode" placeholder="—">
                  </orbix-search-select>
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
                      [disabled]="busy() || f.invalid"
                      [title]="submitTooltip()">
                @if (busy()) {
                  <span class="spinner-border spinner-border-sm"></span>
                } @else {
                  <i class="bi" [class.bi-save]="editing()" [class.bi-plus-lg]="!editing()"></i>
                }
                {{ editing() ? 'Save changes' : 'Create supplier' }}
              </button>
              <button type="button" class="btn btn-outline-secondary" (click)="toggleForm()"
                      title="Discard changes and close the form">Cancel</button>
            </div>
          </form>
        </div>
      </div>
    }

    @if (!showForm()) {
    <div class="card border-0 shadow-sm mb-3">
      <div class="card-body p-3 d-flex flex-wrap align-items-center gap-3">
        <div class="search-box flex-grow-1">
          <i class="bi bi-search"></i>
          <input type="search" class="form-control" placeholder="Search by code, name or TIN"
                 [(ngModel)]="searchTerm" (ngModelChange)="onSearchChange()">
        </div>
        <div class="status-pills d-flex gap-1 flex-wrap">
          @for (opt of statusOptions; track opt.value) {
            <button type="button" class="status-pill"
                    [class.is-active]="statusFilter() === opt.value"
                    (click)="setFilter(opt.value)">
              {{ opt.label }}
            </button>
          }
        </div>
      </div>
    </div>

    <div class="card border-0 shadow-sm overflow-hidden">
      @if (suppliers().length === 0) {
        <div class="p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-truck"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">
            @if (searchTerm || statusFilter()) {
              No suppliers match
            } @else {
              No suppliers yet
            }
          </h2>
          <p class="small text-secondary mb-0">
            @if (searchTerm) {
              Try a different code, name or TIN.
            } @else if (statusFilter()) {
              Try clearing the status filter.
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
              @for (supplier of suppliers(); track supplier.partyId) {
                <tr [class.table-active]="editing()?.partyId === supplier.partyId">
                  <td><span class="badge text-bg-light border text-secondary font-monospace">{{ supplier.party.code }}</span></td>
                  <td>
                    <div class="fw-semibold text-dark">{{ supplier.party.name }}</div>
                    @if (supplier.defaultCurrencyCode && supplier.defaultCurrencyCode !== 'TZS') {
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
                    <div class="btn-group btn-group-sm">
                      <button class="btn btn-outline-secondary" (click)="startEdit(supplier)"
                              [disabled]="busy()" title="Edit this supplier">
                        <i class="bi bi-pencil"></i>
                      </button>
                      @if (supplier.party.status === 'ACTIVE') {
                        <button class="btn btn-outline-danger" (click)="archive(supplier)"
                                [disabled]="busy()" title="Archive. Affects every role on the underlying party.">
                          <i class="bi bi-archive"></i>
                        </button>
                      } @else {
                        <button class="btn btn-outline-success" (click)="activate(supplier)"
                                [disabled]="busy()" title="Reactivate. Affects every role on the underlying party.">
                          <i class="bi bi-arrow-up-circle"></i>
                        </button>
                      }
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <ul class="list-unstyled mb-0 d-md-none">
          @for (supplier of suppliers(); track supplier.partyId) {
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
              <div class="d-flex gap-2 mt-2">
                <button class="btn btn-sm btn-outline-secondary flex-grow-1" (click)="startEdit(supplier)"
                        [disabled]="busy()">
                  <i class="bi bi-pencil me-1"></i> Edit
                </button>
                @if (supplier.party.status === 'ACTIVE') {
                  <button class="btn btn-sm btn-outline-danger flex-grow-1" (click)="archive(supplier)"
                          [disabled]="busy()">
                    <i class="bi bi-archive me-1"></i> Archive
                  </button>
                } @else {
                  <button class="btn btn-sm btn-outline-success flex-grow-1" (click)="activate(supplier)"
                          [disabled]="busy()">
                    <i class="bi bi-arrow-up-circle me-1"></i> Activate
                  </button>
                }
              </div>
            </li>
          }
        </ul>
      }
      @if (totalPages() >= 1) {
        <div class="card-footer bg-white border-top">
          <orbix-pager [page]="pageNo()" [totalPages]="totalPages()"
                       [totalElements]="total()" [pageSize]="pageSize"
                       (pageChange)="goTo($event)"/>
        </div>
      }
    </div>
    }
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
    .simple-table tbody tr.table-active { background: #eef4ff !important; }
    .simple-table .actions-col { width: 1%; white-space: nowrap; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.25rem 0.625rem; border-radius: 999px;
      font-size: 0.72rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 6px; height: 6px; border-radius: 50%; }
    .status-badge--active   { background: #d1fae5; color: #047857; }
    .status-badge--active .status-badge__dot   { background: #10b981; }
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

    .party-mode-toggle {
      display: inline-flex; gap: 0; padding: 0.25rem;
      background: #f3f4f6; border-radius: 10px; align-self: flex-start;
    }
    .party-mode-toggle__btn {
      padding: 0.4rem 0.95rem; font-size: 0.85rem; font-weight: 500;
      border: none; background: transparent; color: #6b7280; border-radius: 8px;
      transition: background 0.15s ease, color 0.15s ease;
    }
    .party-mode-toggle__btn:hover { color: #1f2937; }
    .party-mode-toggle__btn.is-active {
      background: #fff; color: #0d2a5b; font-weight: 600;
      box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
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
  private readonly currencyService = inject(CurrencyService);

  protected readonly suppliers = signal<Supplier[]>([]);
  protected readonly parties = signal<PartyResponse[]>([]);
  protected readonly currencies = signal<Currency[]>([]);
  protected readonly currencyOptions = computed<SearchSelectOption[]>(() =>
    this.currencies()
      .filter(c => c.status === 'ACTIVE')
      .map(c => ({ id: c.code, label: `${c.code} · ${c.name}` }))
  );
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly tinMatch = signal<PartyResponse | null>(null);
  protected readonly showForm = signal(false);
  protected readonly editing = signal<Supplier | null>(null);
  protected readonly partyMode = signal<'pick' | 'create'>('pick');

  protected readonly partyOptions = computed<SearchSelectOption[]>(() => {
    // Exclusion is best-effort: suppliers() holds only the current page, so a
    // party that's already a supplier on another page may still appear here —
    // the backend rejects the duplicate role with a clear message.
    const supplierIds = new Set(this.suppliers().map(s => s.partyId));
    return this.parties()
      .filter(p => p.status === 'ACTIVE' && !supplierIds.has(p.id))
      .map(p => ({ id: p.id, label: `${p.code} · ${p.name}` }));
  });

  protected readonly submitTooltip = computed(() => {
    if (this.editing()) return 'Save the edits to this supplier';
    return this.partyMode() === 'pick'
      ? 'Attach the supplier role to the chosen party'
      : 'Save the new party and assign the supplier role in one transaction';
  });

  protected readonly statusFilter = signal<'ACTIVE' | 'ARCHIVED' | null>(null);
  protected searchTerm = '';

  protected readonly statusOptions = [
    { label: 'All',      value: null },
    { label: 'Active',   value: 'ACTIVE' as const },
    { label: 'Archived', value: 'ARCHIVED' as const },
  ];

  // --- server-side pagination state ----------------------------------------
  protected readonly pageNo = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected readonly pageSize = 20;
  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  protected partyId: string | null = null;
  protected partyDetails: PartyDetails = blankPartyDetails();
  protected paymentTermsDays = 0;
  protected defaultCurrencyCode: string | null = null;
  protected leadTimeDays: number | null = null;
  protected bankName: string | null = null;
  protected bankAccountNo: string | null = null;

  ngOnInit(): void {
    this.load();
    this.party.listParties().subscribe({
      next: list => this.parties.set(list),
      error: () => this.parties.set([])
    });
    this.currencyService.listCurrencies().subscribe({
      next: list => this.currencies.set(list),
      error: () => this.currencies.set([])
    });
  }

  toggleForm(): void {
    this.showForm.update(v => !v);
    if (!this.showForm()) this.reset();
  }

  setPartyMode(mode: 'pick' | 'create'): void {
    if (this.partyMode() === mode) return;
    this.partyMode.set(mode);
    if (mode === 'pick') {
      this.partyDetails = blankPartyDetails();
      this.tinMatch.set(null);
    } else {
      this.partyId = null;
    }
  }

  checkTin(tin: string): void {
    this.party.findByTin(tin).subscribe(match => this.tinMatch.set(match));
  }

  startEdit(supplier: Supplier): void {
    this.editing.set(supplier);
    this.paymentTermsDays = supplier.paymentTermsDays;
    this.defaultCurrencyCode = supplier.defaultCurrencyCode;
    this.leadTimeDays = supplier.leadTimeDays;
    this.bankName = supplier.bankName;
    this.bankAccountNo = supplier.bankAccountNo;
    this.partyDetails = partyToDetails(supplier.party);
    this.showForm.set(true);
  }

  submit(): void {
    const editing = this.editing();
    if (editing) {
      this.runUpdate(editing);
    } else {
      this.runCreate();
    }
  }

  archive(supplier: Supplier): void {
    this.busy.set(true);
    this.party.archiveSupplier(supplier.party.uid).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  activate(supplier: Supplier): void {
    this.busy.set(true);
    this.party.activateSupplier(supplier.party.uid).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private runCreate(): void {
    this.busy.set(true);
    this.error.set(null);
    const pickMode = this.partyMode() === 'pick';
    this.party.createSupplier({
      partyId: pickMode ? this.partyId : null,
      party: pickMode ? null : this.partyDetails,
      paymentTermsDays: Number(this.paymentTermsDays),
      creditLimitAmount: 0,
      defaultCurrencyCode: this.defaultCurrencyCode,
      bankName: this.bankName,
      bankAccountNo: this.bankAccountNo,
      leadTimeDays: this.leadTimeDays == null ? null : Number(this.leadTimeDays)
    }).subscribe({
      next: () => this.afterSave(),
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private runUpdate(editing: Supplier): void {
    this.busy.set(true);
    this.error.set(null);
    const payload: UpdateSupplierRequest = {
      party: this.partyDetails,
      paymentTermsDays: Number(this.paymentTermsDays),
      creditLimitAmount: editing.creditLimitAmount,
      defaultCurrencyCode: this.defaultCurrencyCode,
      bankName: this.bankName,
      bankAccountNo: this.bankAccountNo,
      leadTimeDays: this.leadTimeDays == null ? null : Number(this.leadTimeDays)
    };
    this.party.updateSupplier(editing.party.uid, payload).subscribe({
      next: () => this.afterSave(),
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private afterSave(): void {
    this.busy.set(false);
    this.reset();
    this.showForm.set(false);
    this.load();
  }

  private reset(): void {
    this.editing.set(null);
    this.partyMode.set('pick');
    this.partyId = null;
    this.partyDetails = blankPartyDetails();
    this.paymentTermsDays = 0;
    this.defaultCurrencyCode = null;
    this.leadTimeDays = null;
    this.bankName = null;
    this.bankAccountNo = null;
    this.tinMatch.set(null);
  }

  onSearchChange(): void {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => { this.pageNo.set(0); this.load(); }, 300);
  }

  setFilter(status: 'ACTIVE' | 'ARCHIVED' | null): void {
    this.statusFilter.set(status);
    this.pageNo.set(0);
    this.load();
  }

  goTo(p: number): void {
    if (p < 0 || p >= this.totalPages()) return;
    this.pageNo.set(p);
    this.load();
  }

  private load(): void {
    this.party.listSuppliers(this.searchTerm, this.statusFilter(), this.pageNo(), this.pageSize).subscribe({
      next: pageData => {
        this.suppliers.set(pageData.content);
        this.total.set(pageData.totalElements);
        this.totalPages.set(pageData.totalPages);
        this.pageNo.set(pageData.page);
      },
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

function partyToDetails(p: PartyResponse): PartyDetails {
  return {
    name: p.name,
    legalName: p.legalName,
    category: p.category,
    tin: p.tin,
    vrn: p.vrn,
    phone: p.phone,
    email: p.email,
    physicalAddress: p.physicalAddress,
    postalAddress: p.postalAddress,
    countryCode: p.countryCode,
    notes: p.notes
  };
}

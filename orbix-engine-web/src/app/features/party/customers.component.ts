import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { SearchSelectComponent, SearchSelectOption } from '../../core/ui/search-select.component';
import { PagerComponent } from '../../core/ui/pager.component';
import { PartyService } from './party.service';
import { CatalogService } from '../catalog/catalog.service';
import { PriceList } from '../catalog/catalog.models';
import { PartyDetailsFormComponent } from './party-details-form.component';
import {
  Customer,
  PartyDetails,
  PartyResponse,
  UpdateCustomerRequest,
  blankPartyDetails
} from './party.models';

@Component({
  selector: 'orbix-customers',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DecimalPipe, PartyDetailsFormComponent, SearchSelectComponent, PagerComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Parties</a> &rsaquo; Customers
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Customers</h1>
        <p class="text-secondary mb-0 small">{{ total() }} customer{{ total() === 1 ? '' : 's' }} on file.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleForm()"
              [title]="showForm() ? 'Close the form without saving' : 'Open the form to register a new customer'">
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

    @if (showForm()) {
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">
            {{ editing() ? 'Edit customer' : 'New customer' }}
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
                Creating will attach the customer role to that existing party.
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
                        title="Promote an existing party (e.g. a supplier or employee already on file) into the customer role.">
                  <i class="bi bi-person-check me-1"></i> Pick existing party
                </button>
                <button type="button" class="party-mode-toggle__btn"
                        [class.is-active]="partyMode() === 'create'"
                        (click)="setPartyMode('create')"
                        title="Register a brand-new party and assign the customer role in one step.">
                  <i class="bi bi-person-plus me-1"></i> Create new party
                </button>
              </div>

              @if (partyMode() === 'pick') {
                <div class="row g-2">
                  <div class="col-md-12">
                    <label class="form-label small fw-semibold text-secondary">Party</label>
                    <orbix-search-select name="party" [options]="partyOptions()"
                                         [(ngModel)]="partyId" placeholder="Pick a supplier / employee / agent…" required>
                    </orbix-search-select>
                    <p class="form-text small mb-0">Promotes the chosen party into the customer role.</p>
                  </div>
                </div>
              } @else {
                <p class="form-text small mb-0">
                  <i class="bi bi-info-circle me-1"></i>
                  Party code is auto-generated from the <span class="font-monospace">CUST</span> sequence on save.
                </p>

                <orbix-party-details-form [details]="partyDetails" (tinBlur)="checkTin($event)" />
              }
            }

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
                <div class="col-md-8">
                  <label class="form-label small fw-semibold text-secondary" for="customerPriceList">Price list <span class="text-muted">(optional)</span></label>
                  <orbix-search-select id="customerPriceList" name="pl"
                                       [options]="priceListOptions()"
                                       [(ngModel)]="selectedPriceListId"
                                       placeholder="Select a price list…">
                  </orbix-search-select>
                  <p class="form-text small mb-0">Overrides the company default price list for this customer's sales.</p>
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
                {{ editing() ? 'Save changes' : 'Create customer' }}
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
      @if (customers().length === 0) {
        <div class="p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-person-circle"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">
            @if (searchTerm || statusFilter()) {
              No customers match
            } @else {
              No customers yet
            }
          </h2>
          <p class="small text-secondary mb-0">
            @if (searchTerm) {
              Try a different code, name or TIN.
            } @else if (statusFilter()) {
              Try clearing the status filter.
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
              @for (customer of customers(); track customer.partyId) {
                <tr [class.table-active]="editing()?.partyId === customer.partyId">
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
                    @if (!customer.walkIn) {
                      <div class="btn-group btn-group-sm">
                        <button class="btn btn-outline-secondary" (click)="startEdit(customer)"
                                [disabled]="busy()" title="Edit this customer">
                          <i class="bi bi-pencil"></i>
                        </button>
                        @if (customer.party.status === 'ACTIVE') {
                          <button class="btn btn-outline-danger" (click)="archive(customer)"
                                  [disabled]="busy()" title="Archive. Affects every role on the underlying party.">
                            <i class="bi bi-archive"></i>
                          </button>
                        } @else {
                          <button class="btn btn-outline-success" (click)="activate(customer)"
                                  [disabled]="busy()" title="Reactivate. Affects every role on the underlying party.">
                            <i class="bi bi-arrow-up-circle"></i>
                          </button>
                        }
                      </div>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <ul class="list-unstyled mb-0 d-md-none">
          @for (customer of customers(); track customer.partyId) {
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
              @if (!customer.walkIn) {
                <div class="d-flex gap-2 mt-2">
                  <button class="btn btn-sm btn-outline-secondary flex-grow-1" (click)="startEdit(customer)"
                          [disabled]="busy()">
                    <i class="bi bi-pencil me-1"></i> Edit
                  </button>
                  @if (customer.party.status === 'ACTIVE') {
                    <button class="btn btn-sm btn-outline-danger flex-grow-1" (click)="archive(customer)"
                            [disabled]="busy()">
                      <i class="bi bi-archive me-1"></i> Archive
                    </button>
                  } @else {
                    <button class="btn btn-sm btn-outline-success flex-grow-1" (click)="activate(customer)"
                            [disabled]="busy()">
                      <i class="bi bi-arrow-up-circle me-1"></i> Activate
                    </button>
                  }
                </div>
              }
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
export class CustomersComponent implements OnInit {
  private readonly party = inject(PartyService);
  private readonly catalog = inject(CatalogService);

  protected readonly customers = signal<Customer[]>([]);
  protected readonly parties = signal<PartyResponse[]>([]);
  protected readonly priceLists = signal<PriceList[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly tinMatch = signal<PartyResponse | null>(null);
  protected readonly showForm = signal(false);
  protected readonly editing = signal<Customer | null>(null);
  protected readonly partyMode = signal<'pick' | 'create'>('pick');

  protected readonly partyOptions = computed<SearchSelectOption[]>(() => {
    // Exclusion is best-effort: customers() holds only the current page, so a
    // party that's already a customer on another page may still appear here —
    // the backend rejects the duplicate role with a clear message.
    const customerIds = new Set(this.customers().map(c => c.partyId));
    return this.parties()
      .filter(p => p.status === 'ACTIVE' && !customerIds.has(p.id))
      .map(p => ({ id: p.id, label: `${p.code} · ${p.name}` }));
  });

  protected readonly priceListOptions = computed<SearchSelectOption[]>(() =>
    this.priceLists()
      .filter(pl => pl.status === 'ACTIVE')
      .map(pl => ({ id: pl.id, label: `${pl.code} · ${pl.name}` }))
  );

  protected readonly submitTooltip = computed(() => {
    if (this.editing()) return 'Save the edits to this customer';
    return this.partyMode() === 'pick'
      ? 'Attach the customer role to the chosen party'
      : 'Save the new party and assign the customer role in one transaction';
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
  protected creditLimitAmount = 0;
  protected creditTermsDays = 0;
  protected taxExempt = false;
  /** Bound to the price-list picker; null means "use company default". */
  protected selectedPriceListId: string | null = null;

  ngOnInit(): void {
    this.load();
    this.party.listParties().subscribe({
      next: list => this.parties.set(list),
      error: () => this.parties.set([])
    });
    this.catalog.listPriceLists().subscribe({
      next: list => this.priceLists.set(list),
      error: () => this.priceLists.set([])
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

  startEdit(customer: Customer): void {
    this.editing.set(customer);
    this.creditLimitAmount = customer.creditLimitAmount;
    this.creditTermsDays = customer.creditTermsDays;
    this.taxExempt = customer.taxExempt;
    this.selectedPriceListId = customer.priceListId;
    this.partyDetails = partyToDetails(customer.party);
    this.showForm.set(true);
  }

  submit(): void {
    const editing = this.editing();
    if (editing) {
      this.runUpdate(editing.party.uid);
    } else {
      this.runCreate();
    }
  }

  archive(customer: Customer): void {
    this.busy.set(true);
    this.party.archiveCustomer(customer.party.uid).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  activate(customer: Customer): void {
    this.busy.set(true);
    this.party.activateCustomer(customer.party.uid).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private runCreate(): void {
    this.busy.set(true);
    this.error.set(null);
    const pickMode = this.partyMode() === 'pick';
    this.party.createCustomer({
      partyId: pickMode ? this.partyId : null,
      party: pickMode ? null : this.partyDetails,
      creditLimitAmount: Number(this.creditLimitAmount),
      creditTermsDays: Number(this.creditTermsDays),
      priceListId: this.selectedPriceListId,
      defaultSalesAgentId: null,
      defaultBranchId: null,
      taxExempt: this.taxExempt
    }).subscribe({
      next: () => this.afterSave(),
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private runUpdate(partyUid: string): void {
    this.busy.set(true);
    this.error.set(null);
    const editing = this.editing()!;
    const payload: UpdateCustomerRequest = {
      party: this.partyDetails,
      creditLimitAmount: Number(this.creditLimitAmount),
      creditTermsDays: Number(this.creditTermsDays),
      priceListId: this.selectedPriceListId,
      defaultSalesAgentId: editing.defaultSalesAgentId,
      defaultBranchId: editing.defaultBranchId,
      taxExempt: this.taxExempt
    };
    this.party.updateCustomer(partyUid, payload).subscribe({
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
    this.creditLimitAmount = 0;
    this.creditTermsDays = 0;
    this.taxExempt = false;
    this.selectedPriceListId = null;
    this.tinMatch.set(null);
  }

  onSearchChange(): void {
    // Debounce so we don't hit the server on every keystroke.
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
    this.party.listCustomers(this.searchTerm, this.statusFilter(), this.pageNo(), this.pageSize).subscribe({
      next: pageData => {
        this.customers.set(pageData.content);
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

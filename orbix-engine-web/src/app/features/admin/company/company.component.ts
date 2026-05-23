import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CompanyService } from './company.service';
import { Company, UpdateCompanyRequest } from './company.models';
import { FilterableSelectComponent, FilterOption } from '../../../shared/filter-select.component';
import { CurrencyAdminService } from '../currencies/currency-admin.service';

@Component({
  selector: 'orbix-company-profile',
  standalone: true,
  imports: [FormsModule, FilterableSelectComponent],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Settings</p>
      <h1 class="h3 fw-bold mb-1 text-dark">Company profile</h1>
      <p class="text-secondary mb-0 small">Identity, tax and document defaults that appear on invoices and reports.</p>
    </header>

    @if (info()) { <div class="alert alert-success py-2 px-3 small">{{ info() }}</div> }
    @if (error()) { <div class="alert alert-danger py-2 px-3 small">{{ error() }}</div> }

    @if (form(); as f) {
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-body">
          <div class="row g-3">
            <div class="col-md-3">
              <label class="form-label small mb-1">Code</label>
              <input class="form-control form-control-sm" [value]="code()" disabled>
            </div>
            <div class="col-md-5">
              <label class="form-label small mb-1">Name *</label>
              <input class="form-control form-control-sm" [(ngModel)]="f.name" name="name">
            </div>
            <div class="col-md-4">
              <label class="form-label small mb-1">Legal name</label>
              <input class="form-control form-control-sm" [(ngModel)]="f.legalName" name="legalName">
            </div>

            <div class="col-md-3">
              <label class="form-label small mb-1">TIN</label>
              <input class="form-control form-control-sm" [(ngModel)]="f.tin" name="tin">
            </div>
            <div class="col-md-3">
              <label class="form-label small mb-1">VRN</label>
              <input class="form-control form-control-sm" [(ngModel)]="f.vrn" name="vrn">
            </div>
            <div class="col-md-3">
              <label class="form-label small mb-1">Currency *</label>
              <orbix-filter-select [options]="currencyOptions()" [value]="f.currencyCode"
                                   (valueChange)="f.currencyCode = $event ?? ''" placeholder="Search currency…"/>
            </div>
            <div class="col-md-2">
              <label class="form-label small mb-1">Country *</label>
              <input class="form-control form-control-sm text-uppercase" maxlength="2" [(ngModel)]="f.countryCode" name="countryCode">
            </div>
            <div class="col-md-4">
              <label class="form-label small mb-1">Time zone *</label>
              <orbix-filter-select [options]="tzOptions()" [value]="f.timeZone"
                                   (valueChange)="f.timeZone = $event ?? ''" placeholder="Search time zone…"/>
            </div>

            <div class="col-md-4">
              <label class="form-label small mb-1">Phone</label>
              <input class="form-control form-control-sm" [(ngModel)]="f.phone" name="phone">
            </div>
            <div class="col-md-4">
              <label class="form-label small mb-1">Email</label>
              <input class="form-control form-control-sm" [(ngModel)]="f.email" name="email">
            </div>
            <div class="col-md-4">
              <label class="form-label small mb-1">Website</label>
              <input class="form-control form-control-sm" [(ngModel)]="f.website" name="website">
            </div>

            <div class="col-md-6">
              <label class="form-label small mb-1">Physical address</label>
              <textarea class="form-control form-control-sm" rows="2" [(ngModel)]="f.physicalAddress" name="physicalAddress"></textarea>
            </div>
            <div class="col-md-6">
              <label class="form-label small mb-1">Postal address</label>
              <textarea class="form-control form-control-sm" rows="2" [(ngModel)]="f.postalAddress" name="postalAddress"></textarea>
            </div>

            <div class="col-md-6">
              <label class="form-label small mb-1">Default invoice note</label>
              <textarea class="form-control form-control-sm" rows="2" [(ngModel)]="f.defaultInvoiceNote" name="defaultInvoiceNote"></textarea>
            </div>
            <div class="col-md-6">
              <label class="form-label small mb-1">Default quotation note</label>
              <textarea class="form-control form-control-sm" rows="2" [(ngModel)]="f.defaultQuotationNote" name="defaultQuotationNote"></textarea>
            </div>
          </div>
        </div>
      </div>

      <div class="d-flex gap-2">
        <button class="btn btn-primary" (click)="save()" [disabled]="saving() || !f.name?.trim()">
          {{ saving() ? 'Saving…' : 'Save profile' }}
        </button>
        <button class="btn btn-outline-secondary" (click)="reload()" [disabled]="saving()">Discard</button>
      </div>
    }
  `
})
export class CompanyProfileComponent implements OnInit {
  private readonly api = inject(CompanyService);
  private readonly currencyApi = inject(CurrencyAdminService);

  protected readonly form = signal<UpdateCompanyRequest | null>(null);
  protected readonly code = signal('');
  protected readonly saving = signal(false);
  protected readonly info = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly currencyOptions = signal<FilterOption[]>([]);
  protected readonly tzOptions = signal<FilterOption[]>(loadTimeZones());

  ngOnInit(): void {
    this.reload();
    this.currencyApi.listCurrencies().subscribe({
      next: list => this.currencyOptions.set(
        list.map(c => ({ value: c.code, label: `${c.code} — ${c.name}` }))),
      error: () => this.currencyOptions.set([])
    });
  }

  reload(): void {
    this.info.set(null);
    this.error.set(null);
    this.api.get().subscribe({
      next: c => this.apply(c),
      error: () => this.error.set('Failed to load company profile.')
    });
  }

  save(): void {
    const f = this.form();
    if (!f) return;
    this.saving.set(true);
    this.error.set(null);
    this.api.update(f).subscribe({
      next: c => { this.apply(c); this.saving.set(false); this.info.set('Company profile saved.'); },
      error: err => { this.saving.set(false); this.error.set(err?.error?.message ?? 'Failed to save.'); }
    });
  }

  private apply(c: Company): void {
    this.code.set(c.code);
    this.form.set({
      name: c.name,
      legalName: c.legalName,
      tin: c.tin,
      vrn: c.vrn,
      physicalAddress: c.physicalAddress,
      postalAddress: c.postalAddress,
      phone: c.phone,
      email: c.email,
      website: c.website,
      currencyCode: c.currencyCode,
      countryCode: c.countryCode,
      timeZone: c.timeZone,
      defaultInvoiceNote: c.defaultInvoiceNote,
      defaultQuotationNote: c.defaultQuotationNote
    });
  }
}

/** IANA time zones from the browser (Intl), with a small fallback for older engines. */
function loadTimeZones(): FilterOption[] {
  const intl = Intl as unknown as { supportedValuesOf?: (key: string) => string[] };
  let zones: string[];
  try {
    zones = intl.supportedValuesOf ? intl.supportedValuesOf('timeZone') : [];
  } catch {
    zones = [];
  }
  if (zones.length === 0) {
    zones = ['UTC', 'Africa/Kampala', 'Africa/Nairobi', 'Africa/Dar_es_Salaam', 'Africa/Kigali',
      'Africa/Lagos', 'Africa/Johannesburg', 'Europe/London', 'America/New_York'];
  }
  return zones.map(z => ({ value: z, label: z }));
}

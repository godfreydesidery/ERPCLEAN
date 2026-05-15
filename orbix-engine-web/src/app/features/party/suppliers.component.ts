import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { PartyService } from './party.service';
import { PartyDetailsFormComponent } from './party-details-form.component';
import { PartyDetails, PartyResponse, Supplier, blankPartyDetails } from './party.models';

@Component({
  selector: 'orbix-suppliers',
  standalone: true,
  imports: [FormsModule, PartyDetailsFormComponent],
  template: `
    <h2 class="h3 mb-4">Suppliers</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <table class="table table-sm align-middle">
      <thead>
        <tr><th>Code</th><th>Name</th><th>TIN</th><th>Terms</th><th>Lead time</th>
            <th>Status</th><th></th></tr>
      </thead>
      <tbody>
        @for (supplier of suppliers(); track supplier.partyId) {
          <tr>
            <td>{{ supplier.party.code }}</td>
            <td>{{ supplier.party.name }}</td>
            <td>{{ supplier.party.tin ?? '—' }}</td>
            <td>{{ supplier.paymentTermsDays }}d</td>
            <td>{{ supplier.leadTimeDays ?? '—' }}</td>
            <td>
              @if (supplier.party.status === 'ACTIVE') {
                <span class="badge text-bg-success">ACTIVE</span>
              } @else {
                <span class="badge text-bg-warning">{{ supplier.party.status }}</span>
              }
            </td>
            <td class="text-end">
              @if (supplier.party.status === 'ACTIVE') {
                <button class="btn btn-sm btn-outline-danger" (click)="deactivate(supplier)"
                        [disabled]="busy()">Deactivate</button>
              }
            </td>
          </tr>
        } @empty {
          <tr><td colspan="7" class="text-muted">No suppliers yet.</td></tr>
        }
      </tbody>
    </table>

    <div class="card shadow-sm">
      <div class="card-header fw-semibold">New supplier</div>
      <div class="card-body">
        @if (tinMatch(); as match) {
          <div class="alert alert-info py-2">
            A party with this TIN already exists — <strong>{{ match.code }} · {{ match.name }}</strong>.
            Creating will attach the supplier role to that existing party.
          </div>
        }
        <form (ngSubmit)="create()" #f="ngForm">
          <div class="row g-2 mb-2">
            <div class="col-md-3">
              <label class="form-label">Code</label>
              <input class="form-control" name="code" [(ngModel)]="code" required>
            </div>
          </div>
          <orbix-party-details-form [details]="partyDetails" (tinBlur)="checkTin($event)" />
          <div class="row g-2 mt-2">
            <div class="col-md-3">
              <label class="form-label">Payment terms (days)</label>
              <input class="form-control" type="number" name="pt" [(ngModel)]="paymentTermsDays">
            </div>
            <div class="col-md-3">
              <label class="form-label">Default currency</label>
              <input class="form-control" maxlength="3" name="ccy" [(ngModel)]="defaultCurrencyCode">
            </div>
            <div class="col-md-3">
              <label class="form-label">Lead time (days)</label>
              <input class="form-control" type="number" name="lt" [(ngModel)]="leadTimeDays">
            </div>
          </div>
          <div class="row g-2 mt-1">
            <div class="col-md-4">
              <label class="form-label">Bank name</label>
              <input class="form-control" name="bn" [(ngModel)]="bankName">
            </div>
            <div class="col-md-4">
              <label class="form-label">Bank account no.</label>
              <input class="form-control" name="ba" [(ngModel)]="bankAccountNo">
            </div>
          </div>
          <button class="btn btn-primary mt-3" [disabled]="busy() || f.invalid">Create supplier</button>
        </form>
      </div>
    </div>
  `
})
export class SuppliersComponent implements OnInit {
  private readonly party = inject(PartyService);

  readonly suppliers = signal<Supplier[]>([]);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly tinMatch = signal<PartyResponse | null>(null);

  code = '';
  partyDetails: PartyDetails = blankPartyDetails();
  paymentTermsDays = 0;
  defaultCurrencyCode: string | null = null;
  leadTimeDays: number | null = null;
  bankName: string | null = null;
  bankAccountNo: string | null = null;

  ngOnInit(): void {
    this.load();
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
      leadTimeDays: this.leadTimeDays != null ? Number(this.leadTimeDays) : null
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.reset();
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

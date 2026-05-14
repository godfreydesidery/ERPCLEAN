import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { PartyService } from './party.service';
import { PartyDetailsFormComponent } from './party-details-form.component';
import { Customer, PartyDetails, PartyResponse, blankPartyDetails } from './party.models';

@Component({
  selector: 'orbix-customers',
  standalone: true,
  imports: [FormsModule, PartyDetailsFormComponent],
  template: `
    <h2 class="h3 mb-4">Customers</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <table class="table table-sm align-middle">
      <thead>
        <tr><th>Code</th><th>Name</th><th>TIN</th><th class="text-end">Credit limit</th>
            <th>Terms</th><th>Status</th><th></th></tr>
      </thead>
      <tbody>
        @for (customer of customers(); track customer.partyId) {
          <tr>
            <td>{{ customer.party.code }}</td>
            <td>
              {{ customer.party.name }}
              @if (customer.walkIn) { <span class="badge text-bg-secondary ms-1">walk-in</span> }
            </td>
            <td>{{ customer.party.tin ?? '—' }}</td>
            <td class="text-end">{{ customer.creditLimitAmount }}</td>
            <td>{{ customer.creditTermsDays }}d</td>
            <td>
              @if (customer.party.status === 'ACTIVE') {
                <span class="badge text-bg-success">ACTIVE</span>
              } @else {
                <span class="badge text-bg-warning">{{ customer.party.status }}</span>
              }
            </td>
            <td class="text-end">
              @if (customer.party.status === 'ACTIVE' && !customer.walkIn) {
                <button class="btn btn-sm btn-outline-danger" (click)="deactivate(customer)"
                        [disabled]="busy()">Deactivate</button>
              }
            </td>
          </tr>
        } @empty {
          <tr><td colspan="7" class="text-muted">No customers yet.</td></tr>
        }
      </tbody>
    </table>

    <div class="card shadow-sm">
      <div class="card-header fw-semibold">New customer</div>
      <div class="card-body">
        @if (tinMatch(); as match) {
          <div class="alert alert-info py-2">
            A party with this TIN already exists — <strong>{{ match.code }} · {{ match.name }}</strong>.
            Creating will attach the customer role to that existing party.
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
              <label class="form-label">Credit limit</label>
              <input class="form-control" type="number" name="cl" [(ngModel)]="creditLimitAmount">
            </div>
            <div class="col-md-3">
              <label class="form-label">Credit terms (days)</label>
              <input class="form-control" type="number" name="ct" [(ngModel)]="creditTermsDays">
            </div>
            <div class="col-md-3 d-flex align-items-end">
              <div class="form-check">
                <input class="form-check-input" type="checkbox" id="texempt" name="texempt"
                       [(ngModel)]="taxExempt">
                <label class="form-check-label" for="texempt">Tax exempt</label>
              </div>
            </div>
          </div>
          <button class="btn btn-primary mt-3" [disabled]="busy() || f.invalid">Create customer</button>
        </form>
      </div>
    </div>
  `
})
export class CustomersComponent implements OnInit {
  private readonly party = inject(PartyService);

  readonly customers = signal<Customer[]>([]);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly tinMatch = signal<PartyResponse | null>(null);

  code = '';
  partyDetails: PartyDetails = blankPartyDetails();
  creditLimitAmount = 0;
  creditTermsDays = 0;
  taxExempt = false;

  ngOnInit(): void {
    this.load();
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

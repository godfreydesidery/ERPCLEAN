import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { PartyService } from './party.service';
import { PartyDetailsFormComponent } from './party-details-form.component';
import { PartyDetails, SalesAgent, blankPartyDetails } from './party.models';

@Component({
  selector: 'orbix-sales-agents',
  standalone: true,
  imports: [FormsModule, PartyDetailsFormComponent],
  template: `
    <h2 class="h3 mb-4">Sales agents</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <table class="table table-sm align-middle">
      <thead>
        <tr><th>Agent code</th><th>Name</th><th>Route</th><th>Commission</th><th>Branch</th>
            <th>Status</th><th></th></tr>
      </thead>
      <tbody>
        @for (agent of agents(); track agent.partyId) {
          <tr>
            <td>{{ agent.agentCode }}</td>
            <td>{{ agent.party.name }}</td>
            <td>{{ agent.routeCode ?? '—' }}</td>
            <td>{{ agent.commissionRate != null ? (agent.commissionRate * 100) + '%' : '—' }}</td>
            <td>{{ agent.branchId }}</td>
            <td>
              @if (agent.party.status === 'ACTIVE') {
                <span class="badge text-bg-success">ACTIVE</span>
              } @else {
                <span class="badge text-bg-warning">{{ agent.party.status }}</span>
              }
            </td>
            <td class="text-end">
              @if (agent.party.status === 'ACTIVE') {
                <button class="btn btn-sm btn-outline-danger" (click)="deactivate(agent)"
                        [disabled]="busy()">Deactivate</button>
              }
            </td>
          </tr>
        } @empty {
          <tr><td colspan="7" class="text-muted">No sales agents yet.</td></tr>
        }
      </tbody>
    </table>

    <div class="card shadow-sm">
      <div class="card-header fw-semibold">New sales agent</div>
      <div class="card-body">
        <form (ngSubmit)="create()" #f="ngForm">
          <div class="row g-2 mb-2">
            <div class="col-md-3">
              <label class="form-label">Party code</label>
              <input class="form-control" name="code" [(ngModel)]="code" required>
            </div>
            <div class="col-md-3">
              <label class="form-label">Agent code</label>
              <input class="form-control" name="acode" [(ngModel)]="agentCode" required>
            </div>
          </div>
          <orbix-party-details-form [details]="partyDetails" />
          <div class="row g-2 mt-2">
            <div class="col-md-3">
              <label class="form-label">Route code</label>
              <input class="form-control" name="rc" [(ngModel)]="routeCode">
            </div>
            <div class="col-md-3">
              <label class="form-label">Commission rate <small class="text-muted">(0–1)</small></label>
              <input class="form-control" type="number" step="0.0001" name="cr"
                     [(ngModel)]="commissionRate">
            </div>
            <div class="col-md-3">
              <label class="form-label">Branch id</label>
              <input class="form-control" type="number" name="branch" [(ngModel)]="branchId" required>
            </div>
          </div>
          <button class="btn btn-primary mt-3" [disabled]="busy() || f.invalid">Create sales agent</button>
        </form>
      </div>
    </div>
  `
})
export class SalesAgentsComponent implements OnInit {
  private readonly party = inject(PartyService);

  readonly agents = signal<SalesAgent[]>([]);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  code = '';
  agentCode = '';
  partyDetails: PartyDetails = blankPartyDetails();
  routeCode: string | null = null;
  commissionRate: number | null = null;
  branchId: number | null = null;

  ngOnInit(): void {
    this.load();
  }

  create(): void {
    this.busy.set(true);
    this.error.set(null);
    this.party.createSalesAgent({
      code: this.code.trim(),
      party: this.partyDetails,
      agentCode: this.agentCode.trim(),
      appUserId: null,
      routeCode: this.routeCode,
      commissionRate: this.commissionRate != null ? Number(this.commissionRate) : null,
      branchId: Number(this.branchId)
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.reset();
        this.load();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  deactivate(agent: SalesAgent): void {
    this.busy.set(true);
    this.party.deactivateSalesAgent(agent.partyId).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private reset(): void {
    this.code = '';
    this.agentCode = '';
    this.partyDetails = blankPartyDetails();
    this.routeCode = null;
    this.commissionRate = null;
    this.branchId = null;
  }

  private load(): void {
    this.party.listSalesAgents().subscribe({
      next: list => this.agents.set(list),
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

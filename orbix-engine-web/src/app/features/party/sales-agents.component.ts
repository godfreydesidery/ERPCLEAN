import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AccessibleBranch, BranchService } from '../../core/branch/branch.service';
import { Route, RouteService } from '../../core/route/route.service';
import { SearchSelectComponent, SearchSelectOption } from '../../core/ui/search-select.component';
import { PartyService } from './party.service';
import { PartyDetailsFormComponent } from './party-details-form.component';
import { PartyDetails, PartyResponse, SalesAgent, UpdateSalesAgentRequest, blankPartyDetails } from './party.models';

@Component({
  selector: 'orbix-sales-agents',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, PartyDetailsFormComponent, SearchSelectComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Parties</a> &rsaquo; Sales agents
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Sales agents</h1>
        <p class="text-secondary mb-0 small">{{ agents().length }} agent{{ agents().length === 1 ? '' : 's' }} on file.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleForm()"
              [title]="showForm() ? 'Close the form without saving' : 'Open the form to register a new sales agent'">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()"></i>
        {{ showForm() ? 'Close form' : 'New agent' }}
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
            {{ editing() ? 'Edit sales agent' : 'New sales agent' }}
          </h2>
          <button class="btn-close btn-sm" (click)="toggleForm()" aria-label="Close"
                  title="Close the form without saving"></button>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="submit()" #f="ngForm" class="d-flex flex-column gap-3">
            @if (editing()) {
              <div class="d-flex flex-wrap align-items-center gap-2">
                <span class="text-secondary small">Editing</span>
                <span class="badge text-bg-light border text-secondary font-monospace">{{ agentCode }}</span>
                <span class="text-secondary small">·</span>
                <span class="text-secondary small">Agent code cannot be changed</span>
              </div>

              <orbix-party-details-form [details]="partyDetails" />
            } @else {
              <div class="party-mode-toggle">
                <button type="button" class="party-mode-toggle__btn"
                        [class.is-active]="partyMode() === 'pick'"
                        (click)="setPartyMode('pick')"
                        title="Promote a customer, supplier, or employee already on file into the sales-agent role. Use this when the person is already in the system in another capacity.">
                  <i class="bi bi-person-check me-1"></i> Pick existing party
                </button>
                <button type="button" class="party-mode-toggle__btn"
                        [class.is-active]="partyMode() === 'create'"
                        (click)="setPartyMode('create')"
                        title="Register a brand-new party and assign the sales-agent role in one step. Use this for people who don't yet exist in the system.">
                  <i class="bi bi-person-plus me-1"></i> Create new party
                </button>
              </div>

              @if (partyMode() === 'pick') {
                <div class="row g-2">
                  <div class="col-md-8">
                    <label class="form-label small fw-semibold text-secondary">Party</label>
                    <orbix-search-select name="party" [options]="partyOptions()"
                                         [(ngModel)]="partyId" placeholder="Pick a customer / supplier / employee…" required>
                    </orbix-search-select>
                    <p class="form-text small mb-0">Promotes the chosen party into the sales-agent role.</p>
                  </div>
                  <div class="col-md-4">
                    <label class="form-label small fw-semibold text-secondary">Agent code</label>
                    <input class="form-control font-monospace" name="acode" [(ngModel)]="agentCode" required
                           placeholder="Field ID">
                  </div>
                </div>
              } @else {
                <div class="row g-2">
                  <div class="col-md-4">
                    <label class="form-label small fw-semibold text-secondary">Agent code</label>
                    <input class="form-control font-monospace" name="acode" [(ngModel)]="agentCode" required
                           placeholder="Field ID">
                  </div>
                  <div class="col-md-8 d-flex align-items-end">
                    <p class="form-text small mb-0">
                      <i class="bi bi-info-circle me-1"></i>
                      Party code is auto-generated from the <span class="font-monospace">AGT</span> sequence on save.
                    </p>
                  </div>
                </div>

                <orbix-party-details-form [details]="partyDetails" />
              }
            }

            <fieldset class="role-fieldset">
              <legend class="role-fieldset__legend">
                <i class="bi bi-signpost-split text-secondary"></i> Route &amp; commission
              </legend>
              <div class="row g-2">
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">
                    Route <span class="text-muted">(optional)</span>
                  </label>
                  <orbix-search-select name="route" [options]="routeOptions()"
                                       [(ngModel)]="routeId" placeholder="—">
                  </orbix-search-select>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">
                    Commission rate <span class="text-muted">(0–1)</span>
                  </label>
                  <input class="form-control text-end" type="number" step="0.0001" name="cr"
                         [(ngModel)]="commissionRate" placeholder="0.05">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Branch</label>
                  <orbix-search-select name="branch" [options]="branchOptions()"
                                       [(ngModel)]="branchId" placeholder="Select a branch…" required>
                  </orbix-search-select>
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
                {{ editing() ? 'Save changes' : 'Create sales agent' }}
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
          <input type="search" class="form-control" placeholder="Search by agent code, name or route"
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

    <div class="card border-0 shadow-sm overflow-hidden">
      @if (filtered().length === 0) {
        <div class="p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-graph-up-arrow"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">No sales agents match</h2>
          <p class="small text-secondary mb-0">
            @if (searchTerm) {
              Try a different code, name or route.
            } @else {
              Add your first sales agent to start tracking routes.
            }
          </p>
        </div>
      } @else {
        <div class="table-responsive d-none d-md-block">
          <table class="table table-hover align-middle mb-0 simple-table">
            <thead>
              <tr>
                <th>Agent code</th><th>Name</th><th>Route</th>
                <th class="text-end">Commission</th><th>Branch</th><th>Status</th>
                <th class="text-end actions-col"></th>
              </tr>
            </thead>
            <tbody>
              @for (agent of filtered(); track agent.partyId) {
                <tr>
                  <td><span class="badge text-bg-light border text-secondary font-monospace">{{ agent.agentCode }}</span></td>
                  <td class="fw-semibold text-dark">{{ agent.party.name }}</td>
                  <td class="small text-secondary">{{ routeLabel(agent.routeId) }}</td>
                  <td class="text-end fw-semibold">
                    {{ agent.commissionRate != null ? (agent.commissionRate * 100).toFixed(2) + '%' : '—' }}
                  </td>
                  <td class="small text-secondary">{{ branchLabel(agent.branchId) }}</td>
                  <td>
                    <span class="status-badge status-badge--{{ agent.party.status.toLowerCase() }}">
                      <span class="status-badge__dot"></span>{{ agent.party.status }}
                    </span>
                  </td>
                  <td class="text-end actions-col">
                    <div class="btn-group btn-group-sm">
                      <button class="btn btn-outline-secondary" (click)="startEdit(agent)"
                              [disabled]="busy()" title="Edit this agent — change party details, route, branch, or commission. Agent code stays fixed.">
                        <i class="bi bi-pencil"></i>
                      </button>
                      @if (agent.party.status === 'ACTIVE') {
                        <button class="btn btn-outline-danger" (click)="deactivate(agent)"
                                [disabled]="busy()" title="Deactivate this agent. Also deactivates every other role on the underlying party (e.g. their customer record).">
                          <i class="bi bi-pause-circle"></i>
                        </button>
                      } @else {
                        <button class="btn btn-outline-success" (click)="activate(agent)"
                                [disabled]="busy()" title="Reactivate this agent. Also reactivates every other role on the underlying party.">
                          <i class="bi bi-play-circle"></i>
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
          @for (agent of filtered(); track agent.partyId) {
            <li class="party-card">
              <div class="d-flex justify-content-between align-items-start gap-2 mb-1">
                <div class="flex-grow-1">
                  <span class="badge text-bg-light border text-secondary font-monospace mb-1">{{ agent.agentCode }}</span>
                  <p class="fw-semibold text-dark mb-0">{{ agent.party.name }}</p>
                  <p class="small text-secondary mb-0">{{ routeLabel(agent.routeId) }}</p>
                </div>
                <span class="status-badge status-badge--{{ agent.party.status.toLowerCase() }}">
                  <span class="status-badge__dot"></span>{{ agent.party.status }}
                </span>
              </div>
              <div class="d-flex justify-content-between small text-secondary mt-2">
                <span>
                  Commission
                  {{ agent.commissionRate != null ? (agent.commissionRate * 100).toFixed(2) + '%' : '—' }}
                </span>
                <span>{{ branchLabel(agent.branchId) }}</span>
              </div>
              <div class="d-flex gap-2 mt-2">
                <button class="btn btn-sm btn-outline-secondary flex-grow-1" (click)="startEdit(agent)"
                        [disabled]="busy()">
                  <i class="bi bi-pencil me-1"></i> Edit
                </button>
                @if (agent.party.status === 'ACTIVE') {
                  <button class="btn btn-sm btn-outline-danger flex-grow-1" (click)="deactivate(agent)"
                          [disabled]="busy()">
                    <i class="bi bi-pause-circle me-1"></i> Deactivate
                  </button>
                } @else {
                  <button class="btn btn-sm btn-outline-success flex-grow-1" (click)="activate(agent)"
                          [disabled]="busy()">
                    <i class="bi bi-play-circle me-1"></i> Activate
                  </button>
                }
              </div>
            </li>
          }
        </ul>
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
      background: #ede9fe; color: #6d28d9; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .role-fieldset {
      background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 10px; padding: 1rem 1.25rem 1.25rem;
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
export class SalesAgentsComponent implements OnInit {
  private readonly party = inject(PartyService);
  private readonly branchService = inject(BranchService);
  private readonly routeService = inject(RouteService);

  protected readonly agents = signal<SalesAgent[]>([]);
  protected readonly branches = signal<AccessibleBranch[]>([]);
  protected readonly routes = signal<Route[]>([]);
  protected readonly parties = signal<PartyResponse[]>([]);
  protected readonly branchOptions = computed<SearchSelectOption[]>(() =>
    this.branches().map(b => ({ id: b.id, label: `${b.code} · ${b.name}` }))
  );
  protected readonly routeOptions = computed<SearchSelectOption[]>(() =>
    this.routes()
      .filter(r => r.status === 'ACTIVE')
      .map(r => ({ id: r.id, label: `${r.code} · ${r.name}` }))
  );
  // Exclude parties that are already agents — backend would reject them anyway.
  protected readonly partyOptions = computed<SearchSelectOption[]>(() => {
    const agentIds = new Set(this.agents().map(a => a.partyId));
    return this.parties()
      .filter(p => p.status === 'ACTIVE' && !agentIds.has(p.id))
      .map(p => ({ id: p.id, label: `${p.code} · ${p.name}` }));
  });
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
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
    return this.agents().filter(a => {
      if (status && a.party.status !== status) return false;
      if (!q) return true;
      return a.agentCode.toLowerCase().includes(q)
          || a.party.name.toLowerCase().includes(q)
          || this.routeLabel(a.routeId).toLowerCase().includes(q);
    });
  });

  protected readonly partyMode = signal<'pick' | 'create'>('pick');
  protected readonly editing = signal<SalesAgent | null>(null);
  protected readonly submitTooltip = computed(() => {
    if (this.editing()) return 'Save the edits to this agent';
    return this.partyMode() === 'pick'
      ? 'Save and attach the sales-agent role to the chosen party'
      : 'Save the new party and assign the sales-agent role in one transaction';
  });
  protected partyId: string | null = null;
  protected agentCode = '';
  protected partyDetails: PartyDetails = blankPartyDetails();
  protected routeId: string | null = null;
  protected commissionRate: number | null = null;
  protected branchId: string | null = null;

  ngOnInit(): void {
    this.load();
    this.branchService.listBranches().subscribe({
      next: list => this.branches.set(list),
      error: () => this.branches.set([])
    });
    this.routeService.listRoutes().subscribe({
      next: list => this.routes.set(list),
      error: () => this.routes.set([])
    });
    this.party.listParties().subscribe({
      next: list => this.parties.set(list),
      error: () => this.parties.set([])
    });
  }

  setPartyMode(mode: 'pick' | 'create'): void {
    if (this.partyMode() === mode) return;
    this.partyMode.set(mode);
    // Clear the inactive path's fields so they don't leak across modes
    // (e.g. a stale partyId getting submitted from create mode).
    if (mode === 'pick') {
      this.partyDetails = blankPartyDetails();
    } else {
      this.partyId = null;
    }
  }

  protected branchLabel(branchId: string | null): string {
    if (branchId == null) return '—';
    const b = this.branches().find(x => x.id === branchId);
    return b ? `${b.code} · ${b.name}` : `#${branchId}`;
  }

  protected routeLabel(routeId: string | null): string {
    if (routeId == null) return '—';
    const r = this.routes().find(x => x.id === routeId);
    return r ? `${r.code} · ${r.name}` : `#${routeId}`;
  }

  toggleForm(): void {
    this.showForm.update(v => !v);
    if (!this.showForm()) this.reset();
  }

  submit(): void {
    const editing = this.editing();
    if (editing) {
      this.runUpdate(editing.partyId);
    } else {
      this.runCreate();
    }
  }

  startEdit(agent: SalesAgent): void {
    this.editing.set(agent);
    this.agentCode = agent.agentCode;
    this.routeId = agent.routeId;
    this.commissionRate = agent.commissionRate;
    this.branchId = agent.branchId;
    this.partyDetails = {
      name: agent.party.name,
      legalName: agent.party.legalName,
      category: agent.party.category,
      tin: agent.party.tin,
      vrn: agent.party.vrn,
      phone: agent.party.phone,
      email: agent.party.email,
      physicalAddress: agent.party.physicalAddress,
      postalAddress: agent.party.postalAddress,
      countryCode: agent.party.countryCode,
      notes: agent.party.notes
    };
    this.showForm.set(true);
  }

  deactivate(agent: SalesAgent): void {
    this.busy.set(true);
    this.party.deactivateSalesAgent(agent.partyId).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  activate(agent: SalesAgent): void {
    this.busy.set(true);
    this.party.activateSalesAgent(agent.partyId).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private runCreate(): void {
    this.busy.set(true);
    this.error.set(null);
    const pickMode = this.partyMode() === 'pick';
    this.party.createSalesAgent({
      partyId: pickMode ? this.partyId : null,
      party: pickMode ? null : this.partyDetails,
      agentCode: this.agentCode.trim(),
      appUserId: null,
      routeId: this.routeId,
      commissionRate: this.commissionRate == null ? null : Number(this.commissionRate),
      branchId: this.branchId!
    }).subscribe({
      next: () => this.afterSave(),
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private runUpdate(partyId: string): void {
    this.busy.set(true);
    this.error.set(null);
    const payload: UpdateSalesAgentRequest = {
      party: this.partyDetails,
      appUserId: null,
      routeId: this.routeId,
      commissionRate: this.commissionRate == null ? null : Number(this.commissionRate),
      branchId: this.branchId!
    };
    this.party.updateSalesAgent(partyId, payload).subscribe({
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
    this.agentCode = '';
    this.partyDetails = blankPartyDetails();
    this.routeId = null;
    this.commissionRate = null;
    this.branchId = null;
  }

  private load(): void {
    this.party.listSalesAgents().subscribe({
      next: list => this.agents.set(list),
      error: err => this.showError(err)
    });
    // Refresh the parties list too — a "create new" path adds a party that the
    // picker should henceforth exclude (it'd now be an agent).
    this.party.listParties().subscribe({
      next: list => this.parties.set(list),
      error: () => { /* swallow — listParties is auxiliary for the picker */ }
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

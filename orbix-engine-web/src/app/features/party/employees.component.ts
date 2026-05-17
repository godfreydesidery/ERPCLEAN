import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AccessibleBranch, BranchService } from '../../core/branch/branch.service';
import { SearchSelectComponent, SearchSelectOption } from '../../core/ui/search-select.component';
import { PartyService } from './party.service';
import { PartyDetailsFormComponent } from './party-details-form.component';
import {
  Employee,
  PartyDetails,
  PartyResponse,
  UpdateEmployeeRequest,
  blankPartyDetails
} from './party.models';

@Component({
  selector: 'orbix-employees',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, PartyDetailsFormComponent, SearchSelectComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Parties</a> &rsaquo; Employees
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Employees</h1>
        <p class="text-secondary mb-0 small">{{ employees().length }} employee{{ employees().length === 1 ? '' : 's' }} on the roster.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleForm()"
              [title]="showForm() ? 'Close the form without saving' : 'Open the form to register a new employee'">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()"></i>
        {{ showForm() ? 'Close form' : 'New employee' }}
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
            {{ editing() ? 'Edit employee' : 'New employee' }}
          </h2>
          <button class="btn-close btn-sm" (click)="toggleForm()" aria-label="Close"
                  title="Close the form without saving"></button>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="submit()" #f="ngForm" class="d-flex flex-column gap-3">
            @if (editing()) {
              <div class="d-flex flex-wrap align-items-center gap-2">
                <span class="text-secondary small">Editing</span>
                <span class="badge text-bg-light border text-secondary font-monospace">{{ employeeCode }}</span>
                <span class="text-secondary small">·</span>
                <span class="text-secondary small">Employee code cannot be changed</span>
              </div>

              <orbix-party-details-form [details]="partyDetails" />
            } @else {
              <div class="party-mode-toggle">
                <button type="button" class="party-mode-toggle__btn"
                        [class.is-active]="partyMode() === 'pick'"
                        (click)="setPartyMode('pick')"
                        title="Promote an existing party (e.g. a customer or supplier already on file) into the employee role.">
                  <i class="bi bi-person-check me-1"></i> Pick existing party
                </button>
                <button type="button" class="party-mode-toggle__btn"
                        [class.is-active]="partyMode() === 'create'"
                        (click)="setPartyMode('create')"
                        title="Register a brand-new party and assign the employee role in one step.">
                  <i class="bi bi-person-plus me-1"></i> Create new party
                </button>
              </div>

              @if (partyMode() === 'pick') {
                <div class="row g-2">
                  <div class="col-md-8">
                    <label class="form-label small fw-semibold text-secondary">Party</label>
                    <orbix-search-select name="party" [options]="partyOptions()"
                                         [(ngModel)]="partyId" placeholder="Pick a customer / supplier / agent…" required>
                    </orbix-search-select>
                    <p class="form-text small mb-0">Promotes the chosen party into the employee role.</p>
                  </div>
                  <div class="col-md-4">
                    <label class="form-label small fw-semibold text-secondary">Employee code</label>
                    <input class="form-control font-monospace" name="ecode" [(ngModel)]="employeeCode" required
                           placeholder="HR ID">
                  </div>
                </div>
              } @else {
                <div class="row g-2">
                  <div class="col-md-4">
                    <label class="form-label small fw-semibold text-secondary">Employee code</label>
                    <input class="form-control font-monospace" name="ecode" [(ngModel)]="employeeCode" required
                           placeholder="HR ID">
                  </div>
                  <div class="col-md-8 d-flex align-items-end">
                    <p class="form-text small mb-0">
                      <i class="bi bi-info-circle me-1"></i>
                      Party code is auto-generated from the <span class="font-monospace">EMP</span> sequence on save.
                    </p>
                  </div>
                </div>

                <orbix-party-details-form [details]="partyDetails" />
              }
            }

            <fieldset class="role-fieldset">
              <legend class="role-fieldset__legend">
                <i class="bi bi-briefcase text-secondary"></i> Employment
              </legend>
              <div class="row g-2">
                <div class="col-md-5">
                  <label class="form-label small fw-semibold text-secondary">Job title</label>
                  <input class="form-control" name="jt" [(ngModel)]="jobTitle" placeholder="e.g. Cashier">
                </div>
                <div class="col-md-3">
                  <label class="form-label small fw-semibold text-secondary">Branch</label>
                  <orbix-search-select name="branch" [options]="branchOptions()"
                                       [(ngModel)]="branchId" placeholder="Select a branch…" required>
                  </orbix-search-select>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Hire date</label>
                  <input class="form-control" type="date" name="hd" [(ngModel)]="hireDate">
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
                {{ editing() ? 'Save changes' : 'Create employee' }}
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
          <input type="search" class="form-control" placeholder="Search by employee code, name or title"
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
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-person-badge"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">No employees match</h2>
          <p class="small text-secondary mb-0">
            @if (searchTerm) {
              Try a different code or name.
            } @else {
              Add your first employee to the roster.
            }
          </p>
        </div>
      } @else {
        <div class="table-responsive d-none d-md-block">
          <table class="table table-hover align-middle mb-0 simple-table">
            <thead>
              <tr>
                <th>Employee code</th><th>Name</th><th>Job title</th>
                <th>Branch</th><th>Hired</th><th>Status</th>
                <th class="text-end actions-col"></th>
              </tr>
            </thead>
            <tbody>
              @for (employee of filtered(); track employee.partyId) {
                <tr [class.table-active]="editing()?.partyId === employee.partyId">
                  <td><span class="badge text-bg-light border text-secondary font-monospace">{{ employee.employeeCode }}</span></td>
                  <td class="fw-semibold text-dark">{{ employee.party.name }}</td>
                  <td class="small text-secondary">{{ employee.jobTitle ?? '—' }}</td>
                  <td class="small text-secondary">{{ branchLabel(employee.branchId) }}</td>
                  <td class="small text-secondary">{{ employee.hireDate ? (employee.hireDate | date:'mediumDate') : '—' }}</td>
                  <td>
                    <span class="status-badge status-badge--{{ employee.party.status.toLowerCase() }}">
                      <span class="status-badge__dot"></span>{{ employee.party.status }}
                    </span>
                  </td>
                  <td class="text-end actions-col">
                    <div class="btn-group btn-group-sm">
                      <button class="btn btn-outline-secondary" (click)="startEdit(employee)"
                              [disabled]="busy()" title="Edit this employee — employee code stays fixed.">
                        <i class="bi bi-pencil"></i>
                      </button>
                      @if (employee.party.status === 'ACTIVE') {
                        <button class="btn btn-outline-danger" (click)="deactivate(employee)"
                                [disabled]="busy()" title="Deactivate. Affects every role on the underlying party.">
                          <i class="bi bi-pause-circle"></i>
                        </button>
                      } @else {
                        <button class="btn btn-outline-success" (click)="activate(employee)"
                                [disabled]="busy()" title="Reactivate. Affects every role on the underlying party.">
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
          @for (employee of filtered(); track employee.partyId) {
            <li class="party-card">
              <div class="d-flex justify-content-between align-items-start gap-2 mb-1">
                <div class="flex-grow-1">
                  <span class="badge text-bg-light border text-secondary font-monospace mb-1">{{ employee.employeeCode }}</span>
                  <p class="fw-semibold text-dark mb-0">{{ employee.party.name }}</p>
                  <p class="small text-secondary mb-0">{{ employee.jobTitle ?? '—' }}</p>
                </div>
                <span class="status-badge status-badge--{{ employee.party.status.toLowerCase() }}">
                  <span class="status-badge__dot"></span>{{ employee.party.status }}
                </span>
              </div>
              <div class="d-flex justify-content-between small text-secondary mt-2">
                <span>{{ branchLabel(employee.branchId) }}</span>
                <span>{{ employee.hireDate ? (employee.hireDate | date:'mediumDate') : '—' }}</span>
              </div>
              <div class="d-flex gap-2 mt-2">
                <button class="btn btn-sm btn-outline-secondary flex-grow-1" (click)="startEdit(employee)"
                        [disabled]="busy()">
                  <i class="bi bi-pencil me-1"></i> Edit
                </button>
                @if (employee.party.status === 'ACTIVE') {
                  <button class="btn btn-sm btn-outline-danger flex-grow-1" (click)="deactivate(employee)"
                          [disabled]="busy()">
                    <i class="bi bi-pause-circle me-1"></i> Deactivate
                  </button>
                } @else {
                  <button class="btn btn-sm btn-outline-success flex-grow-1" (click)="activate(employee)"
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
    .status-badge--inactive { background: #fef3c7; color: #92400e; }
    .status-badge--inactive .status-badge__dot { background: #f59e0b; }
    .status-badge--archived { background: #f3f4f6; color: #4b5563; }
    .status-badge--archived .status-badge__dot { background: #9ca3af; }

    .party-card { padding: 1rem; border-bottom: 1px solid #f3f4f6; }
    .party-card:last-child { border-bottom: none; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #fef3c7; color: #b45309; font-size: 1.75rem;
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
export class EmployeesComponent implements OnInit {
  private readonly party = inject(PartyService);
  private readonly branchService = inject(BranchService);

  protected readonly employees = signal<Employee[]>([]);
  protected readonly parties = signal<PartyResponse[]>([]);
  protected readonly branches = signal<AccessibleBranch[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showForm = signal(false);
  protected readonly editing = signal<Employee | null>(null);
  protected readonly partyMode = signal<'pick' | 'create'>('pick');

  protected readonly partyOptions = computed<SearchSelectOption[]>(() => {
    const employeeIds = new Set(this.employees().map(e => e.partyId));
    return this.parties()
      .filter(p => p.status === 'ACTIVE' && !employeeIds.has(p.id))
      .map(p => ({ id: p.id, label: `${p.code} · ${p.name}` }));
  });

  protected readonly branchOptions = computed<SearchSelectOption[]>(() =>
    this.branches().map(b => ({ id: b.id, label: `${b.code} · ${b.name}` }))
  );

  protected readonly submitTooltip = computed(() => {
    if (this.editing()) return 'Save the edits to this employee';
    return this.partyMode() === 'pick'
      ? 'Attach the employee role to the chosen party'
      : 'Save the new party and assign the employee role in one transaction';
  });

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
    return this.employees().filter(e => {
      if (status && e.party.status !== status) return false;
      if (!q) return true;
      return e.employeeCode.toLowerCase().includes(q)
          || e.party.name.toLowerCase().includes(q)
          || (e.jobTitle?.toLowerCase().includes(q) ?? false);
    });
  });

  protected partyId: number | null = null;
  protected employeeCode = '';
  protected partyDetails: PartyDetails = blankPartyDetails();
  protected jobTitle: string | null = null;
  protected branchId: number | null = null;
  protected hireDate: string | null = null;

  ngOnInit(): void {
    this.load();
    this.branchService.listBranches().subscribe({
      next: list => this.branches.set(list),
      error: () => this.branches.set([])
    });
    this.party.listParties().subscribe({
      next: list => this.parties.set(list),
      error: () => this.parties.set([])
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
    } else {
      this.partyId = null;
    }
  }

  protected branchLabel(branchId: number | null): string {
    if (branchId == null) return '—';
    const b = this.branches().find(x => x.id === branchId);
    return b ? `${b.code} · ${b.name}` : `#${branchId}`;
  }

  startEdit(employee: Employee): void {
    this.editing.set(employee);
    this.employeeCode = employee.employeeCode;
    this.jobTitle = employee.jobTitle;
    this.branchId = employee.branchId;
    this.hireDate = employee.hireDate;
    this.partyDetails = partyToDetails(employee.party);
    this.showForm.set(true);
  }

  submit(): void {
    const editing = this.editing();
    if (editing) {
      this.runUpdate(editing.partyId);
    } else {
      this.runCreate();
    }
  }

  deactivate(employee: Employee): void {
    this.busy.set(true);
    this.party.deactivateEmployee(employee.partyId).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  activate(employee: Employee): void {
    this.busy.set(true);
    this.party.activateEmployee(employee.partyId).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private runCreate(): void {
    this.busy.set(true);
    this.error.set(null);
    const pickMode = this.partyMode() === 'pick';
    this.party.createEmployee({
      partyId: pickMode ? this.partyId : null,
      party: pickMode ? null : this.partyDetails,
      employeeCode: this.employeeCode.trim(),
      appUserId: null,
      jobTitle: this.jobTitle,
      branchId: Number(this.branchId),
      hireDate: this.hireDate,
      terminationDate: null
    }).subscribe({
      next: () => this.afterSave(),
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private runUpdate(partyId: number): void {
    this.busy.set(true);
    this.error.set(null);
    const editing = this.editing()!;
    const payload: UpdateEmployeeRequest = {
      party: this.partyDetails,
      appUserId: editing.appUserId,
      jobTitle: this.jobTitle,
      branchId: Number(this.branchId),
      hireDate: this.hireDate,
      terminationDate: editing.terminationDate
    };
    this.party.updateEmployee(partyId, payload).subscribe({
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
    this.employeeCode = '';
    this.partyDetails = blankPartyDetails();
    this.jobTitle = null;
    this.branchId = null;
    this.hireDate = null;
  }

  private load(): void {
    this.party.listEmployees().subscribe({
      next: list => this.employees.set(list),
      error: err => this.showError(err)
    });
    this.party.listParties().subscribe({
      next: list => this.parties.set(list),
      error: () => { /* picker is auxiliary */ }
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

import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { PartyService } from './party.service';
import { PartyDetailsFormComponent } from './party-details-form.component';
import { Employee, PartyDetails, blankPartyDetails } from './party.models';

@Component({
  selector: 'orbix-employees',
  standalone: true,
  imports: [FormsModule, PartyDetailsFormComponent],
  template: `
    <h2 class="h3 mb-4">Employees</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <table class="table table-sm align-middle">
      <thead>
        <tr><th>Employee code</th><th>Name</th><th>Job title</th><th>Branch</th>
            <th>Status</th><th></th></tr>
      </thead>
      <tbody>
        @for (employee of employees(); track employee.partyId) {
          <tr>
            <td>{{ employee.employeeCode }}</td>
            <td>{{ employee.party.name }}</td>
            <td>{{ employee.jobTitle ?? '—' }}</td>
            <td>{{ employee.branchId }}</td>
            <td>
              @if (employee.party.status === 'ACTIVE') {
                <span class="badge text-bg-success">ACTIVE</span>
              } @else {
                <span class="badge text-bg-warning">{{ employee.party.status }}</span>
              }
            </td>
            <td class="text-end">
              @if (employee.party.status === 'ACTIVE') {
                <button class="btn btn-sm btn-outline-danger" (click)="deactivate(employee)"
                        [disabled]="busy()">Deactivate</button>
              }
            </td>
          </tr>
        } @empty {
          <tr><td colspan="6" class="text-muted">No employees yet.</td></tr>
        }
      </tbody>
    </table>

    <div class="card shadow-sm">
      <div class="card-header fw-semibold">New employee</div>
      <div class="card-body">
        <form (ngSubmit)="create()" #f="ngForm">
          <div class="row g-2 mb-2">
            <div class="col-md-3">
              <label class="form-label">Party code</label>
              <input class="form-control" name="code" [(ngModel)]="code" required>
            </div>
            <div class="col-md-3">
              <label class="form-label">Employee code</label>
              <input class="form-control" name="ecode" [(ngModel)]="employeeCode" required>
            </div>
          </div>
          <orbix-party-details-form [details]="partyDetails" />
          <div class="row g-2 mt-2">
            <div class="col-md-4">
              <label class="form-label">Job title</label>
              <input class="form-control" name="jt" [(ngModel)]="jobTitle">
            </div>
            <div class="col-md-3">
              <label class="form-label">Branch id</label>
              <input class="form-control" type="number" name="branch" [(ngModel)]="branchId" required>
            </div>
            <div class="col-md-3">
              <label class="form-label">Hire date</label>
              <input class="form-control" type="date" name="hd" [(ngModel)]="hireDate">
            </div>
          </div>
          <button class="btn btn-primary mt-3" [disabled]="busy() || f.invalid">Create employee</button>
        </form>
      </div>
    </div>
  `
})
export class EmployeesComponent implements OnInit {
  private readonly party = inject(PartyService);

  readonly employees = signal<Employee[]>([]);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  code = '';
  employeeCode = '';
  partyDetails: PartyDetails = blankPartyDetails();
  jobTitle: string | null = null;
  branchId: number | null = null;
  hireDate: string | null = null;

  ngOnInit(): void {
    this.load();
  }

  create(): void {
    this.busy.set(true);
    this.error.set(null);
    this.party.createEmployee({
      code: this.code.trim(),
      party: this.partyDetails,
      employeeCode: this.employeeCode.trim(),
      appUserId: null,
      jobTitle: this.jobTitle,
      branchId: Number(this.branchId),
      hireDate: this.hireDate,
      terminationDate: null
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.reset();
        this.load();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  deactivate(employee: Employee): void {
    this.busy.set(true);
    this.party.deactivateEmployee(employee.partyId).subscribe({
      next: () => { this.busy.set(false); this.load(); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private reset(): void {
    this.code = '';
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

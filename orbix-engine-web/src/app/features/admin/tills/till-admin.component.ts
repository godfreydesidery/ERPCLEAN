import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../../core/api/api-response';
import { AuthService } from '../../../core/auth/auth.service';
import { BranchService } from '../../../core/branch/branch.service';
import { TillAdminService } from './till-admin.service';
import { Till, TillSession } from './till-admin.models';

@Component({
  selector: 'orbix-till-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2 class="h3 mb-4">POS tills + sessions</h2>
    @if (error()) { <div class="alert alert-danger py-2">{{ error() }}</div> }
    @if (info()) { <div class="alert alert-success py-2">{{ info() }}</div> }

    <div class="row g-4">
      <div class="col-12 col-lg-5">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Tills</div>
          <div class="list-group list-group-flush">
            @for (t of tills(); track t.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between"
                      [class.active]="selectedTill()?.id === t.id" (click)="selectTill(t)">
                <span>{{ t.code }} — {{ t.name }}
                  <small class="d-block text-muted">price list #{{ t.defaultPriceListId }}</small>
                </span>
                <span class="badge align-self-center"
                      [class.text-bg-success]="t.status === 'ACTIVE'"
                      [class.text-bg-secondary]="t.status === 'INACTIVE'">{{ t.status }}</span>
              </button>
            } @empty { <div class="list-group-item text-muted">No tills yet.</div> }
          </div>
        </div>

        <div class="card shadow-sm mt-3">
          <div class="card-header fw-semibold">New till</div>
          <div class="card-body">
            <form (ngSubmit)="createTill()" #f="ngForm">
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label small mb-1">Code</label>
                  <input class="form-control" name="cd" [(ngModel)]="newCode" required>
                </div>
                <div class="col">
                  <label class="form-label small mb-1">Name</label>
                  <input class="form-control" name="nm" [(ngModel)]="newName" required>
                </div>
              </div>
              <div class="mb-2">
                <label class="form-label small mb-1">Default price list id</label>
                <input class="form-control" type="number" name="pl" [(ngModel)]="newPriceListId" required>
              </div>
              <button class="btn btn-primary w-100" [disabled]="busy() || f.invalid">Create till</button>
            </form>
          </div>
        </div>

        @if (selectedTill(); as t) {
          <div class="card shadow-sm mt-3">
            <div class="card-header fw-semibold">Open session on {{ t.code }}</div>
            <div class="card-body">
              <form (ngSubmit)="openSession(t)" #of="ngForm">
                <div class="mb-2">
                  <label class="form-label small mb-1">Opening float</label>
                  <input class="form-control" type="number" step="0.0001" min="0"
                         name="of" [(ngModel)]="newFloat" required>
                </div>
                <button class="btn btn-primary w-100" [disabled]="busy() || of.invalid">Open session</button>
              </form>
              <div class="mt-2 d-flex gap-2">
                @if (t.status === 'ACTIVE') {
                  <button class="btn btn-sm btn-outline-danger" [disabled]="busy()"
                          (click)="deactivate(t)">Deactivate till</button>
                } @else {
                  <button class="btn btn-sm btn-outline-success" [disabled]="busy()"
                          (click)="activate(t)">Activate till</button>
                }
              </div>
            </div>
          </div>
        }
      </div>

      <div class="col-12 col-lg-7">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Sessions</div>
          <div class="card-body p-0">
            <table class="table table-sm align-middle mb-0">
              <thead>
                <tr>
                  <th>#</th><th>Till</th><th>Day</th><th>Status</th>
                  <th class="text-end">Float</th>
                  <th class="text-end">Expected</th>
                  <th class="text-end">Declared</th>
                  <th class="text-end">Variance</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (s of sessions(); track s.id) {
                  <tr [class.table-warning]="s.varianceAmount !== null && abs(s.varianceAmount) > 0">
                    <td>{{ s.id }}</td>
                    <td>#{{ s.tillId }}</td>
                    <td>{{ s.businessDate }}</td>
                    <td>
                      <span class="badge"
                            [class.text-bg-info]="s.status === 'OPEN'"
                            [class.text-bg-secondary]="s.status === 'CLOSED'"
                            [class.text-bg-success]="s.status === 'RECONCILED'">{{ s.status }}</span>
                    </td>
                    <td class="text-end">{{ s.openingFloatAmount | number:'1.2-2' }}</td>
                    <td class="text-end">{{ s.expectedCashAmount ?? '—' }}</td>
                    <td class="text-end">{{ s.declaredCashAmount ?? '—' }}</td>
                    <td class="text-end">{{ s.varianceAmount ?? '—' }}</td>
                    <td class="text-end">
                      @if (s.status === 'OPEN') {
                        <button class="btn btn-sm btn-outline-primary"
                                [disabled]="busy()" (click)="closeSession(s)">Close</button>
                      }
                      @if (s.status === 'CLOSED') {
                        <button class="btn btn-sm btn-outline-success"
                                [disabled]="busy()" (click)="reconcileSession(s)">Reconcile</button>
                      }
                    </td>
                  </tr>
                } @empty { <tr><td colspan="9" class="text-muted">No sessions yet.</td></tr> }
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  `
})
export class TillAdminComponent implements OnInit {
  private readonly api = inject(TillAdminService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly tills = signal<Till[]>([]);
  readonly sessions = signal<TillSession[]>([]);
  readonly selectedTill = signal<Till | null>(null);
  readonly busy = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);

  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  newCode = '';
  newName = '';
  newPriceListId: number | null = null;
  newFloat: number | null = null;

  ngOnInit(): void { this.refresh(); }

  refresh(): void {
    this.api.listTills(this.branchId()).subscribe({
      next: rows => this.tills.set(rows),
      error: err => this.showError(err)
    });
    this.api.listSessions(this.branchId()).subscribe({
      next: rows => this.sessions.set(rows),
      error: err => this.showError(err)
    });
  }

  selectTill(t: Till): void { this.selectedTill.set(t); }
  abs(n: number): number { return Math.abs(n); }

  createTill(): void {
    const branchId = this.branchId();
    if (branchId === null || this.newPriceListId === null) {
      this.error.set('Branch + price list id required.');
      return;
    }
    this.busy.set(true);
    this.api.createTill({
      branchId,
      code: this.newCode.trim().toUpperCase(),
      name: this.newName.trim(),
      defaultPriceListId: this.newPriceListId,
      installId: null
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.info.set(`Till ${this.newCode} created.`);
        this.newCode = ''; this.newName = ''; this.newPriceListId = null;
        this.refresh();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  openSession(till: Till): void {
    if (this.newFloat === null) { this.error.set('Opening float required.'); return; }
    this.busy.set(true);
    this.api.openSession({ tillId: till.id, openingFloatAmount: this.newFloat }).subscribe({
      next: () => {
        this.busy.set(false);
        this.info.set(`Session opened on ${till.code}.`);
        this.newFloat = null;
        this.refresh();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  closeSession(s: TillSession): void {
    const declared = window.prompt(`Declared cash for session #${s.id}?`);
    if (!declared) return;
    const amount = parseFloat(declared);
    if (Number.isNaN(amount) || amount < 0) {
      this.error.set('Declared cash must be a non-negative number.');
      return;
    }
    let supervisorIdStr: string | null = null;
    // Pre-flight: if |declared - opening_float| > 1000 (default threshold), the server will demand a supervisor.
    if (Math.abs(amount - s.openingFloatAmount) > 1000) {
      supervisorIdStr = window.prompt('Variance above threshold — supervisor user id?');
      if (!supervisorIdStr) return;
    }
    const supervisorId = supervisorIdStr ? parseInt(supervisorIdStr, 10) : null;
    this.busy.set(true);
    this.api.closeSession(s.id, {
      declaredCashAmount: amount,
      supervisorId,
      notes: null
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.info.set(`Session #${s.id} closed.`);
        this.refresh();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  reconcileSession(s: TillSession): void {
    this.busy.set(true);
    this.api.reconcileSession(s.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.info.set(`Session #${s.id} reconciled.`);
        this.refresh();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  deactivate(t: Till): void {
    this.busy.set(true);
    this.api.deactivateTill(t.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.info.set(`Till ${t.code} deactivated.`);
        this.refresh();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  activate(t: Till): void {
    this.busy.set(true);
    this.api.activateTill(t.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.info.set(`Till ${t.code} activated.`);
        this.refresh();
      },
      error: err => { this.busy.set(false); this.showError(err); }
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

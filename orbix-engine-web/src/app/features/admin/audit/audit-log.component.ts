import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuditService } from './audit.service';
import { AuditFilters, AuditIntegrityResult, AuditLogRow } from './audit.models';

@Component({
  selector: 'orbix-audit-log',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Security</p>
      <h1 class="h3 fw-bold mb-1 text-dark">Audit log</h1>
      <p class="text-secondary mb-0 small">Every recorded action, newest first. Hash-chained and verifiable.</p>
    </header>

    <div class="card border-0 shadow-sm mb-3">
      <div class="card-body">
        <div class="row g-2 align-items-end">
          <div class="col-6 col-md-2">
            <label class="form-label small mb-1">Action</label>
            <input class="form-control form-control-sm" [(ngModel)]="filters.action" placeholder="LOGIN…">
          </div>
          <div class="col-6 col-md-2">
            <label class="form-label small mb-1">Entity type</label>
            <input class="form-control form-control-sm" [(ngModel)]="filters.entityType" placeholder="AppUser…">
          </div>
          <div class="col-6 col-md-2">
            <label class="form-label small mb-1">Entity id</label>
            <input class="form-control form-control-sm" [(ngModel)]="filters.entityId">
          </div>
          <div class="col-6 col-md-2">
            <label class="form-label small mb-1">Actor id</label>
            <input class="form-control form-control-sm" [(ngModel)]="filters.actorId">
          </div>
          <div class="col-6 col-md-2">
            <label class="form-label small mb-1">From</label>
            <input type="datetime-local" class="form-control form-control-sm" [(ngModel)]="filters.from">
          </div>
          <div class="col-6 col-md-2">
            <label class="form-label small mb-1">To</label>
            <input type="datetime-local" class="form-control form-control-sm" [(ngModel)]="filters.to">
          </div>
        </div>
        <div class="d-flex gap-2 mt-3">
          <button class="btn btn-primary btn-sm" (click)="search(0)">Search</button>
          <button class="btn btn-outline-secondary btn-sm" (click)="reset()">Reset</button>
          <button class="btn btn-outline-dark btn-sm ms-auto" (click)="runIntegrity()" [disabled]="checking()">
            {{ checking() ? 'Checking…' : 'Verify integrity' }}
          </button>
        </div>
        @if (integrity(); as r) {
          <div class="alert mt-3 mb-0 py-2 px-3 small" [class.alert-success]="r.ok" [class.alert-danger]="!r.ok">
            <i class="bi" [class.bi-shield-check]="r.ok" [class.bi-shield-exclamation]="!r.ok"></i>
            {{ r.message }}
          </div>
        }
      </div>
    </div>

    @if (error()) {
      <div class="alert alert-danger py-2 px-3 small">{{ error() }}</div>
    }

    <div class="card border-0 shadow-sm">
      <div class="table-responsive">
        <table class="table table-sm table-hover align-middle mb-0">
          <thead class="table-light">
            <tr>
              <th class="small">When</th>
              <th class="small">Actor</th>
              <th class="small">Action</th>
              <th class="small">Entity</th>
              <th class="small">Meta</th>
            </tr>
          </thead>
          <tbody>
            @for (row of rows(); track row.id) {
              <tr>
                <td class="small text-nowrap">{{ row.at | date:'medium' }}</td>
                <td class="small">{{ row.actorId }}</td>
                <td class="small"><span class="badge text-bg-light border">{{ row.action }}</span></td>
                <td class="small">{{ row.entityType }} #{{ row.entityId }}</td>
                <td class="small text-muted text-truncate" style="max-width:280px;">{{ row.metaJson }}</td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="text-center text-secondary small py-4">No audit rows match.</td></tr>
            }
          </tbody>
        </table>
      </div>
      <div class="card-footer d-flex align-items-center justify-content-between small text-secondary">
        <span>{{ total() }} rows · page {{ page() + 1 }} of {{ totalPages() || 1 }}</span>
        <div class="btn-group">
          <button class="btn btn-outline-secondary btn-sm" [disabled]="page() === 0" (click)="search(page() - 1)">Prev</button>
          <button class="btn btn-outline-secondary btn-sm" [disabled]="page() + 1 >= totalPages()" (click)="search(page() + 1)">Next</button>
        </div>
      </div>
    </div>
  `
})
export class AuditLogComponent implements OnInit {
  private readonly api = inject(AuditService);

  protected filters: AuditFilters = {};
  protected readonly rows = signal<AuditLogRow[]>([]);
  protected readonly page = signal(0);
  protected readonly total = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly error = signal<string | null>(null);
  protected readonly checking = signal(false);
  protected readonly integrity = signal<AuditIntegrityResult | null>(null);

  private readonly size = 50;

  ngOnInit(): void {
    this.search(0);
  }

  search(page: number): void {
    this.error.set(null);
    this.api.list(this.normalised(), page, this.size).subscribe({
      next: p => {
        this.rows.set(p.content);
        this.page.set(p.page);
        this.total.set(p.totalElements);
        this.totalPages.set(p.totalPages);
      },
      error: () => this.error.set('Failed to load audit rows.')
    });
  }

  reset(): void {
    this.filters = {};
    this.integrity.set(null);
    this.search(0);
  }

  runIntegrity(): void {
    this.checking.set(true);
    this.api.verify(toIso(this.filters.from), toIso(this.filters.to)).subscribe({
      next: r => { this.integrity.set(r); this.checking.set(false); },
      error: () => {
        this.integrity.set({ ok: false, verifiedCount: 0, firstBrokenId: null, message: 'Integrity check failed.' });
        this.checking.set(false);
      }
    });
  }

  // datetime-local yields "2026-05-23T10:00"; the API wants ISO instants.
  private normalised(): AuditFilters {
    return { ...this.filters, from: toIso(this.filters.from), to: toIso(this.filters.to) };
  }
}

function toIso(local?: string): string | undefined {
  if (!local) return undefined;
  const d = new Date(local);
  return isNaN(d.getTime()) ? undefined : d.toISOString();
}

import {
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ReportExportMenuComponent } from './report-export-menu.component';
import { ReportExport } from './report-export.service';
import { ReportsService } from './reports.service';
import { PartyStatement, StatementEntry } from './reports.models';
import {
  SupplierTypeaheadComponent,
  SupplierSelectedEvent,
} from '../procurement/supplier-typeahead.component';

// Deep-link route map for statement entry kinds (AP).
const KIND_ROUTE: Record<string, string> = {
  INVOICE: '/procurement/supplier-invoices/uid',
  PAYMENT: '/procurement/supplier-payments/uid',
  // TODO (Slice H.1): Vendor-return credit notes are not yet folded into the
  // backend supplier_statement endpoint — the AP statement composes only
  // supplier_invoice (debit) and supplier_payment (credit). When credit notes
  // are added, add CREDIT_NOTE -> '/procurement/vendor-returns/uid' here.
  // See docs/design/slice-k-reports-statements-plan.md §"Out of scope".
};

const KIND_BADGE: Record<string, string> = {
  INVOICE: 'badge text-bg-danger',
  PAYMENT: 'badge text-bg-success',
};

function defaultFrom(): string {
  const d = new Date();
  d.setDate(d.getDate() - 30);
  return d.toISOString().slice(0, 10);
}
function defaultTo(): string {
  return new Date().toISOString().slice(0, 10);
}

@Component({
  selector: 'orbix-supplier-statement',
  standalone: true,
  imports: [
    CommonModule, RouterLink, DatePipe, DecimalPipe,
    FormsModule, ReportExportMenuComponent, SupplierTypeaheadComponent,
  ],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink="/reports" class="text-decoration-none text-secondary">Reports</a> &rsaquo; Supplier statement
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Supplier statement</h1>
        <p class="text-secondary mb-0 small">
          Per-supplier AP statement with opening balance, period activity, and closing balance.
        </p>
      </div>
      @if (statement() && statement()!.entries.length > 0) {
        <orbix-report-export-menu
          [exportBuilder]="buildExport"
          [disabled]="!statement() || statement()!.entries.length === 0">
        </orbix-report-export-menu>
      }
    </header>

    <!-- Filters -->
    <form class="row g-2 mb-4 align-items-end" (ngSubmit)="fetch()" aria-label="Supplier statement filters">
      <div class="col-12 col-md-5">
        <orbix-supplier-typeahead
          instanceId="ss"
          [initialSupplier]="initialSupplier()"
          (supplierSelected)="onSupplierSelected($event)"
          (supplierCleared)="onSupplierCleared()">
        </orbix-supplier-typeahead>
      </div>

      <div class="col-6 col-md-2">
        <label for="ss-from" class="form-label small fw-semibold mb-1">From</label>
        <input id="ss-from" type="date" class="form-control form-control-sm"
               [(ngModel)]="filterFrom" name="filterFrom">
      </div>

      <div class="col-6 col-md-2">
        <label for="ss-to" class="form-label small fw-semibold mb-1">To</label>
        <input id="ss-to" type="date" class="form-control form-control-sm"
               [(ngModel)]="filterTo" name="filterTo">
      </div>

      <div class="col-auto">
        <button type="submit" class="btn btn-primary btn-sm"
                [disabled]="loading() || !selectedSupplier()">
          @if (loading()) {
            <span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span>
          }
          Run
        </button>
      </div>

      @if (!selectedSupplier() && touched()) {
        <div class="col-12">
          <div class="text-danger small">Select a supplier to run the statement.</div>
        </div>
      }
    </form>

    <!-- Error -->
    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2" role="alert">
        <i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" (click)="error.set(null)"
                aria-label="Dismiss error"></button>
      </div>
    }

    <!-- Loading skeleton -->
    @if (loading()) {
      <div aria-live="polite" aria-busy="true" class="visually-hidden">Loading supplier statement…</div>
      <div class="card border-0 shadow-sm p-4 mb-3">
        <div class="placeholder-glow">
          @for (_ of [0,1,2,3]; track $index) {
            <span class="placeholder col-12 d-block mb-2" style="height:28px; border-radius:4px;"></span>
          }
        </div>
      </div>
    }

    <!-- Empty state -->
    @if (!loading() && !error() && fetched() && !statement()?.entries?.length) {
      <div class="card border-0 shadow-sm p-5 text-center" data-testid="report-empty-state">
        <div class="empty-icon mx-auto mb-3">
          <i class="bi bi-truck" aria-hidden="true"></i>
        </div>
        <p class="fw-semibold mb-1">No activity found</p>
        <p class="small text-secondary mb-0">
          This supplier has no transactions in the selected window.
        </p>
      </div>
    }

    <!-- Populated -->
    @if (!loading() && statement() && statement()!.entries.length > 0) {
      <!-- KPI strip -->
      <div class="row g-3 mb-4">
        <div class="col-6 col-md-3">
          <div class="card border-0 shadow-sm p-3 text-center">
            <div class="small text-secondary text-uppercase fw-semibold mb-1" style="letter-spacing:0.06em;">Opening</div>
            <div class="fw-bold font-monospace" [class.text-danger]="statement()!.openingBalance < 0">
              {{ formatMoney(statement()!.openingBalance) }}
            </div>
          </div>
        </div>
        <div class="col-6 col-md-3">
          <div class="card border-0 shadow-sm p-3 text-center">
            <div class="small text-secondary text-uppercase fw-semibold mb-1" style="letter-spacing:0.06em;">Debits</div>
            <div class="fw-bold font-monospace text-danger">{{ formatMoney(statement()!.periodDebits) }}</div>
          </div>
        </div>
        <div class="col-6 col-md-3">
          <div class="card border-0 shadow-sm p-3 text-center">
            <div class="small text-secondary text-uppercase fw-semibold mb-1" style="letter-spacing:0.06em;">Credits</div>
            <div class="fw-bold font-monospace text-success">{{ formatMoney(statement()!.periodCredits) }}</div>
          </div>
        </div>
        <div class="col-6 col-md-3">
          <div class="card border-0 shadow-sm p-3 text-center">
            <div class="small text-secondary text-uppercase fw-semibold mb-1" style="letter-spacing:0.06em;">Closing</div>
            <div class="fw-bold font-monospace" [class.text-danger]="statement()!.closingBalance > 0">
              {{ formatMoney(statement()!.closingBalance) }}
            </div>
          </div>
        </div>
      </div>

      <!-- Statement table -->
      <div class="card border-0 shadow-sm overflow-hidden">
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 statement-table"
                 aria-label="Supplier statement entries">
            <caption class="visually-hidden">
              Supplier statement for {{ selectedSupplier()?.name }}
            </caption>
            <thead>
              <tr>
                <th scope="col">Date</th>
                <th scope="col">Kind</th>
                <th scope="col">Number</th>
                <th scope="col">Reference</th>
                <th scope="col" class="text-end">Debit</th>
                <th scope="col" class="text-end">Credit</th>
                <th scope="col" class="text-end">Balance</th>
              </tr>
            </thead>
            <tbody>
              @for (entry of statement()!.entries; track entry.refId + entry.kind + entry.date) {
                <tr [class.opacity-50]="entry.voided">
                  <td class="text-nowrap small">{{ entry.date | date:'dd/MM/yyyy' }}</td>
                  <td>
                    <span [class]="kindBadge(entry.kind)" style="font-size:0.7rem;">
                      {{ entry.kind }}
                    </span>
                    @if (entry.voided) {
                      <span class="badge text-bg-secondary ms-1" style="font-size:0.65rem;">VOIDED</span>
                    }
                  </td>
                  <td class="small">
                    @if (kindRoute(entry.kind) && entry.refId) {
                      <a [routerLink]="[kindRoute(entry.kind), entry.refId]"
                         class="text-decoration-none">{{ entry.number }}</a>
                    } @else {
                      {{ entry.number }}
                    }
                  </td>
                  <td class="small text-secondary">{{ entry.reference ?? '—' }}</td>
                  <td class="text-end font-monospace small"
                      [class.text-danger]="entry.debit > 0">
                    {{ entry.debit > 0 ? formatMoney(entry.debit) : '—' }}
                  </td>
                  <td class="text-end font-monospace small"
                      [class.text-success]="entry.credit > 0">
                    {{ entry.credit > 0 ? formatMoney(entry.credit) : '—' }}
                  </td>
                  <td class="text-end font-monospace small fw-semibold"
                      [class.text-danger]="entry.balance > 0">
                    {{ formatMoney(entry.balance) }}
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    }
  `,
  styles: [`
    :host { display: block; }
    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #ffe4e6; color: #be123c; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
    .statement-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase;
      letter-spacing: 0.05em; color: #6b7280; background: #f9fafb;
      border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .statement-table tbody td { padding: 0.65rem 1rem; border-bottom: 1px solid #f3f4f6; }
    .statement-table tbody tr:last-child td { border-bottom: none; }
  `],
})
export class SupplierStatementComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(ReportsService);

  protected filterFrom = defaultFrom();
  protected filterTo = defaultTo();

  protected readonly selectedSupplier = signal<SupplierSelectedEvent | null>(null);
  protected readonly initialSupplier = signal<SupplierSelectedEvent | null>(null);
  protected readonly statement = signal<PartyStatement | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly fetched = signal(false);
  protected readonly touched = signal(false);

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      const supplierId = params.get('supplierId');
      const from = params.get('from');
      const to = params.get('to');
      if (from) this.filterFrom = from;
      if (to) this.filterTo = to;

      if (supplierId) {
        const stub: SupplierSelectedEvent = {
          partyUid: '', id: supplierId, code: '', name: `Supplier #${supplierId}`,
        };
        this.initialSupplier.set(stub);
        this.selectedSupplier.set(stub);
        this.fetch();
      }
    });
  }

  protected onSupplierSelected(evt: SupplierSelectedEvent): void {
    this.selectedSupplier.set(evt);
    this.statement.set(null);
    this.fetched.set(false);
    this.error.set(null);
  }

  protected onSupplierCleared(): void {
    this.selectedSupplier.set(null);
    this.statement.set(null);
    this.fetched.set(false);
    this.error.set(null);
  }

  protected fetch(): void {
    this.touched.set(true);
    const supplier = this.selectedSupplier();
    if (!supplier) return;

    this.loading.set(true);
    this.error.set(null);

    this.service.supplierStatement(
      supplier.id,
      this.filterFrom || null,
      this.filterTo || null,
    ).subscribe({
      next: stmt => {
        this.statement.set(stmt);
        this.loading.set(false);
        this.fetched.set(true);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.fetched.set(true);
        this.error.set(extractMessage(err, 'Failed to load supplier statement.'));
      },
    });
  }

  protected kindBadge(kind: string): string {
    return KIND_BADGE[kind] ?? 'badge text-bg-secondary';
  }

  protected kindRoute(kind: string): string | null {
    return KIND_ROUTE[kind] ?? null;
  }

  protected formatMoney(v: number): string {
    return `TZS ${Math.round(v).toLocaleString('en-US')}`;
  }

  readonly buildExport = (): ReportExport => {
    const stmt = this.statement();
    const supplier = this.selectedSupplier();
    const entries: StatementEntry[] = stmt?.entries ?? [];
    return {
      title: 'Supplier Statement',
      subtitle: `Supplier: ${supplier?.name ?? ''} · ${this.filterFrom} to ${this.filterTo}`,
      columns: [
        { key: 'date',      label: 'Date',      format: 'date',     align: 'left'  },
        { key: 'kind',      label: 'Kind',       format: 'text',     align: 'left'  },
        { key: 'number',    label: 'Number',     format: 'text',     align: 'left'  },
        { key: 'reference', label: 'Reference',  format: 'text',     align: 'left'  },
        { key: 'debit',     label: 'Debit',      format: 'currency', align: 'right' },
        { key: 'credit',    label: 'Credit',     format: 'currency', align: 'right' },
        { key: 'balance',   label: 'Balance',    format: 'currency', align: 'right' },
      ],
      rows: entries.map(e => ({
        date:      e.date,
        kind:      e.kind + (e.voided ? ' (VOIDED)' : ''),
        number:    e.number,
        reference: e.reference ?? '',
        debit:     e.debit,
        credit:    e.credit,
        balance:   e.balance,
      })),
      totals: {
        debit:   stmt?.periodDebits   ?? 0,
        credit:  stmt?.periodCredits  ?? 0,
        balance: stmt?.closingBalance ?? 0,
      },
    };
  };
}

function extractMessage(err: unknown, fallback: string): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as { message?: string } | null;
    return body?.message ?? fallback;
  }
  return fallback;
}

import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { DayService } from '../day/day.service';
import { BusinessDay } from '../day/day.models';
import { CompanyService } from '../admin/company/company.service';
import { DashboardService, DASHBOARD_LIVE } from './dashboard.service';

interface Kpi {
  label: string;
  value: string;
  icon: string;
  tint: 'blue' | 'green' | 'amber' | 'rose';
  live: boolean;
}

interface Alert {
  title: string;
  desc: string;
  tint: 'amber' | 'rose' | 'blue' | 'green';
  link: string;
  cta: string;
  live: boolean;
}

interface QuickAction {
  label: string;
  icon: string;
  link: string;
  description: string;
}

@Component({
  selector: 'orbix-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, DatePipe],
  template: `
    <!-- Greeting strip -->
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          {{ today() | date:'EEEE, d MMMM y' }}
        </p>
        <h1 class="h3 fw-bold mb-0 text-dark">{{ greeting() }}, {{ firstName() }}</h1>
        <p class="text-secondary mb-0 small">Here's what's happening across your business today.</p>
      </div>
      <div class="d-flex gap-2">
        <a routerLink="/sales" class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm">
          <i class="bi bi-plus-lg"></i> New sale
        </a>
        <a routerLink="/reports" class="btn btn-outline-secondary d-inline-flex align-items-center gap-2">
          <i class="bi bi-graph-up"></i><span class="d-none d-sm-inline">Reports</span>
        </a>
      </div>
    </header>

    <!-- Business day banner -->
    <section class="card border-0 shadow-sm mb-4 overflow-hidden">
      <div class="card-body p-4 d-flex flex-wrap align-items-center justify-content-between gap-3"
           [class.bg-day-open]="dayStatus() === 'OPEN'"
           [class.bg-day-closing]="dayStatus() === 'CLOSING'"
           [class.bg-day-closed]="dayStatus() === 'CLOSED' || dayStatus() === 'NONE'">
        <div class="d-flex align-items-center gap-3">
          <div class="day-icon">
            <i class="bi" [class.bi-sun]="dayStatus() === 'OPEN'"
                         [class.bi-cloud-sun]="dayStatus() === 'CLOSING'"
                         [class.bi-moon-stars]="dayStatus() === 'CLOSED' || dayStatus() === 'NONE'"></i>
          </div>
          <div>
            <p class="text-uppercase small fw-semibold mb-1 day-eyebrow" style="letter-spacing:0.08em;">
              Business day
            </p>
            <h2 class="h5 fw-bold mb-0">{{ dayHeadline() }}</h2>
            <p class="small mb-0 day-meta">{{ daySubline() }}</p>
          </div>
        </div>
        <div class="d-flex gap-2">
          @if (dayStatus() === 'NONE') {
            <a routerLink="/day" class="btn btn-primary d-inline-flex align-items-center gap-2">
              <i class="bi bi-play-fill"></i> Open day
            </a>
          } @else if (dayStatus() === 'OPEN') {
            <a routerLink="/day" class="btn btn-outline-dark d-inline-flex align-items-center gap-2">
              <i class="bi bi-list-check"></i> Manage day
            </a>
          } @else if (dayStatus() === 'CLOSING') {
            <a routerLink="/day" class="btn btn-warning d-inline-flex align-items-center gap-2 text-dark">
              <i class="bi bi-clipboard-check"></i> Finish close-out
            </a>
          } @else {
            <a routerLink="/day" class="btn btn-outline-secondary d-inline-flex align-items-center gap-2">
              <i class="bi bi-calendar2-week"></i> View ledger
            </a>
          }
        </div>
      </div>
    </section>

    @if (!activeBranchId()) {
      <div class="alert alert-info d-flex align-items-center gap-2 py-2">
        <i class="bi bi-info-circle"></i>
        <span>Pick a branch to see live sales and stock figures.</span>
      </div>
    }

    <!-- KPI grid -->
    <section class="row g-3 g-md-4 mb-4">
      @for (kpi of kpis(); track kpi.label) {
        <div class="col-12 col-sm-6 col-lg-3">
          <div class="card border-0 shadow-sm h-100 kpi-card">
            <div class="card-body p-4">
              <div class="d-flex justify-content-between align-items-start mb-3">
                <span class="kpi-icon kpi-icon--{{ kpi.tint }}">
                  <i class="bi {{ kpi.icon }}"></i>
                </span>
                @if (!kpi.live) {
                  <span class="sample-pill" title="Depicted value — not yet wired to a live source">sample</span>
                }
              </div>
              <p class="text-secondary small fw-semibold mb-1 text-uppercase" style="letter-spacing:0.06em;">
                {{ kpi.label }}
              </p>
              <h3 class="h4 fw-bold mb-0 text-dark">{{ kpi.value }}</h3>
            </div>
          </div>
        </div>
      }
    </section>

    <!-- Quick actions + alerts -->
    <section class="row g-3 g-md-4">
      <div class="col-12 col-lg-7">
        <div class="card border-0 shadow-sm h-100">
          <div class="card-body p-4">
            <div class="d-flex justify-content-between align-items-center mb-3">
              <h2 class="h6 fw-bold mb-0 text-dark">Quick actions</h2>
              <span class="badge text-bg-light text-secondary">Shortcut</span>
            </div>
            <div class="row g-3">
              @for (action of quickActions; track action.label) {
                <div class="col-6 col-md-4">
                  <a [routerLink]="action.link" class="quick-action d-block text-decoration-none">
                    <span class="quick-action__icon">
                      <i class="bi {{ action.icon }}"></i>
                    </span>
                    <span class="quick-action__label">{{ action.label }}</span>
                    <span class="quick-action__desc">{{ action.description }}</span>
                  </a>
                </div>
              }
            </div>
          </div>
        </div>
      </div>
      <div class="col-12 col-lg-5">
        <div class="card border-0 shadow-sm h-100">
          <div class="card-body p-4">
            <div class="d-flex justify-content-between align-items-center mb-3">
              <h2 class="h6 fw-bold mb-0 text-dark">Attention needed</h2>
              <a routerLink="/reports" class="small text-decoration-none">View all</a>
            </div>
            <ul class="list-unstyled mb-0">
              @for (alert of alerts(); track alert.title; let last = $last) {
                <li class="alert-row" [class.alert-row--last]="last">
                  <span class="alert-row__dot alert-row__dot--{{ alert.tint }}"></span>
                  <div class="flex-grow-1">
                    <p class="mb-0 fw-semibold text-dark small">
                      {{ alert.title }}
                      @if (!alert.live) {
                        <span class="sample-pill ms-1" title="Depicted value — not yet wired to a live source">sample</span>
                      }
                    </p>
                    <p class="mb-0 small text-secondary">{{ alert.desc }}</p>
                  </div>
                  <a [routerLink]="alert.link" class="btn btn-sm btn-outline-secondary">{{ alert.cta }}</a>
                </li>
              } @empty {
                <li class="alert-row alert-row--last">
                  <span class="alert-row__dot alert-row__dot--green"></span>
                  <div class="flex-grow-1">
                    <p class="mb-0 fw-semibold text-dark small">All clear</p>
                    <p class="mb-0 small text-secondary">No alerts for the current branch.</p>
                  </div>
                </li>
              }
            </ul>
          </div>
        </div>
      </div>
    </section>
  `,
  styles: [`
    :host {
      display: block;
    }

    /* ---- Business day banner palettes ---- */
    .bg-day-open {
      background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%);
      color: #065f46;
    }
    .bg-day-closing {
      background: linear-gradient(135deg, #fffbeb 0%, #fde68a 100%);
      color: #78350f;
    }
    .bg-day-closed {
      background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
      color: #1e3a8a;
    }
    .day-icon {
      width: 56px;
      height: 56px;
      border-radius: 16px;
      background: rgba(255, 255, 255, 0.6);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.6rem;
      flex-shrink: 0;
    }
    .day-eyebrow { opacity: 0.75; }
    .day-meta { opacity: 0.8; }

    /* ---- KPI tiles ---- */
    .kpi-card { transition: transform 0.15s ease, box-shadow 0.15s ease; }
    .kpi-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 0.5rem 1.5rem rgba(13, 42, 91, 0.08) !important;
    }
    .kpi-icon {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 1.25rem;
    }
    .kpi-icon--blue  { background: #e0ecff; color: #1d4ed8; }
    .kpi-icon--green { background: #d1fae5; color: #047857; }
    .kpi-icon--amber { background: #fef3c7; color: #b45309; }
    .kpi-icon--rose  { background: #ffe4e6; color: #be123c; }

    /* ---- Sample / not-yet-live marker ---- */
    .sample-pill {
      display: inline-block;
      padding: 0.1rem 0.45rem;
      border-radius: 999px;
      font-size: 0.62rem;
      font-weight: 700;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      background: #f1f5f9;
      color: #94a3b8;
      border: 1px dashed #cbd5e1;
      vertical-align: middle;
    }

    /* ---- Quick actions ---- */
    .quick-action {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      padding: 1rem;
      border-radius: 12px;
      border: 1px solid #e5e7eb;
      background: #fff;
      color: inherit;
      transition: all 0.15s ease;
      height: 100%;
    }
    .quick-action:hover {
      border-color: #1d4ed8;
      background: #f8fafc;
      transform: translateY(-1px);
      box-shadow: 0 0.25rem 0.75rem rgba(29, 78, 216, 0.12);
    }
    .quick-action__icon {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: #eef2ff;
      color: #1d4ed8;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 1.1rem;
    }
    .quick-action__label {
      font-weight: 600;
      color: #111827;
      font-size: 0.95rem;
    }
    .quick-action__desc {
      font-size: 0.8rem;
      color: #6b7280;
    }

    /* ---- Alert rows ---- */
    .alert-row {
      display: flex;
      align-items: flex-start;
      gap: 0.875rem;
      padding: 0.875rem 0;
      border-bottom: 1px solid #f1f5f9;
    }
    .alert-row--last { border-bottom: none; padding-bottom: 0; }
    .alert-row:first-child { padding-top: 0; }
    .alert-row__dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      flex-shrink: 0;
      margin-top: 0.4rem;
    }
    .alert-row__dot--amber { background: #f59e0b; }
    .alert-row__dot--rose  { background: #f43f5e; }
    .alert-row__dot--blue  { background: #3b82f6; }
    .alert-row__dot--green { background: #10b981; }

    /* ---- Mobile tightening ---- */
    @media (max-width: 575.98px) {
      .day-icon { width: 48px; height: 48px; font-size: 1.4rem; border-radius: 14px; }
      .quick-action { padding: 0.875rem; }
      .quick-action__icon { width: 36px; height: 36px; font-size: 1rem; }
    }

    @media (prefers-reduced-motion: reduce) {
      .kpi-card, .quick-action { transition: none; }
      .kpi-card:hover, .quick-action:hover { transform: none; }
    }
  `]
})
export class DashboardComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly branch = inject(BranchService);
  private readonly dayService = inject(DayService);
  private readonly company = inject(CompanyService);
  private readonly dashboard = inject(DashboardService);

  protected readonly today = signal(new Date());
  protected readonly activeBranchId = this.branch.activeBranchId;

  protected readonly currentDay = signal<BusinessDay | null>(null);
  protected readonly dayLoaded = signal(false);

  protected readonly currencyCode = signal('TZS');

  // Metric values — null until loaded (or when no branch is selected).
  protected readonly todaysSales = signal<number | null>(null);
  protected readonly stockAlerts = signal<number | null>(null);
  protected readonly openInvoices = signal<number | null>(null);
  protected readonly arOutstanding = signal<number | null>(null);
  protected readonly overdueInvoices = signal<number | null>(null);
  protected readonly lposPending = signal<number | null>(null);
  /** AR tiles show "Permission required" when the caller lacks SALES.REPORT.AR_SUMMARY. */
  protected readonly arPermissionDenied = signal<boolean>(false);

  protected readonly kpis = computed<Kpi[]>(() => {
    const ccy = this.currencyCode();
    const arDenied = this.arPermissionDenied();
    const arValue = arDenied ? 'Permission required' : this.money(this.arOutstanding(), ccy);
    const openValue = arDenied ? 'Permission required' : this.count(this.openInvoices());
    return [
      { label: "Today's sales",  value: this.money(this.todaysSales(), ccy), icon: 'bi-cash-coin', tint: 'green', live: DASHBOARD_LIVE.todaysSales },
      { label: 'Stock alerts',   value: this.count(this.stockAlerts()),      icon: 'bi-box-seam',  tint: 'amber', live: DASHBOARD_LIVE.stockAlertCount },
      { label: 'Open invoices',  value: openValue,                            icon: 'bi-receipt',   tint: 'blue',  live: DASHBOARD_LIVE.openInvoiceCount },
      { label: 'AR outstanding', value: arValue,                              icon: 'bi-people',    tint: 'rose',  live: DASHBOARD_LIVE.arOutstanding },
    ];
  });

  protected readonly alerts = computed<Alert[]>(() => {
    const out: Alert[] = [];
    const stock = this.stockAlerts();
    if (stock && stock > 0) {
      out.push({ tint: 'amber', title: `${stock} SKU${stock === 1 ? '' : 's'} at or below reorder level`,
        desc: 'Review balances and raise a purchase order.', link: '/stock/balances', cta: 'Review',
        live: DASHBOARD_LIVE.stockAlertCount });
    }
    const overdue = this.overdueInvoices();
    if (overdue && overdue > 0) {
      out.push({ tint: 'rose', title: `${overdue} invoice${overdue === 1 ? '' : 's'} past due`,
        desc: 'Chase outstanding receivables under Debt.', link: '/debt', cta: 'Chase',
        live: DASHBOARD_LIVE.overdueInvoiceCount });
    }
    const lpo = this.lposPending();
    if (lpo && lpo > 0) {
      out.push({ tint: 'blue', title: `${lpo} LPO${lpo === 1 ? '' : 's'} awaiting approval`,
        desc: 'Procurement approvals are pending.', link: '/procurement', cta: 'Open',
        live: DASHBOARD_LIVE.lposPendingApproval });
    }
    return out;
  });

  protected readonly dayStatus = computed<'OPEN' | 'CLOSING' | 'CLOSED' | 'NONE'>(() => {
    const d = this.currentDay();
    return d?.status ?? 'NONE';
  });

  protected readonly dayHeadline = computed(() => {
    const d = this.currentDay();
    if (!this.dayLoaded()) return 'Checking business day status…';
    if (!d) return 'No business day open yet';
    switch (d.status) {
      case 'OPEN':    return `Day open · ${d.businessDate}`;
      case 'CLOSING': return `Day closing · ${d.businessDate}`;
      default:        return `Last day closed · ${d.businessDate}`;
    }
  });

  protected readonly daySubline = computed(() => {
    const d = this.currentDay();
    if (!this.dayLoaded()) return 'Fetching the latest from the till.';
    if (!d) return 'Open a day before recording sales.';
    switch (d.status) {
      case 'OPEN':    return 'Sales, receipts, and stock movements are live.';
      case 'CLOSING': return 'Finish the Z-report to close out the day.';
      default:        return 'Open a fresh day to start trading.';
    }
  });

  protected readonly firstName = computed(() => {
    const name = this.auth.currentUser()?.displayName ?? 'there';
    return name.split(/\s+/)[0] || 'there';
  });

  protected readonly greeting = computed(() => {
    const h = this.today().getHours();
    if (h < 12) return 'Good morning';
    if (h < 17) return 'Good afternoon';
    return 'Good evening';
  });

  protected readonly quickActions: QuickAction[] = [
    { label: 'New sale',       icon: 'bi-cart-plus',  link: '/sales',       description: 'Ring up a customer order' },
    { label: 'Receive stock',  icon: 'bi-truck',      link: '/procurement', description: 'Log a GRN from a supplier' },
    { label: 'Add product',    icon: 'bi-tag',        link: '/catalog',     description: 'Create or update an item' },
    { label: 'New customer',   icon: 'bi-person-plus',link: '/party',       description: 'Add a party to the book' },
    { label: 'Stock-take',     icon: 'bi-clipboard2-check', link: '/stock', description: 'Reconcile shelf counts' },
    { label: 'Reports',        icon: 'bi-graph-up',   link: '/reports',     description: 'Sales, AR, AP, profit' },
  ];

  ngOnInit(): void {
    this.company.get().subscribe({
      next: c => this.currencyCode.set(c.currencyCode || 'TZS'),
      error: () => { /* keep default */ },
    });

    // AR tiles — live via /sales/reports/ar-summary. Branch-scoped when an
    // active branch is set; company-wide otherwise. 403 surfaces as the
    // "Permission required" state on the tiles.
    this.loadArSummary();

    this.dashboard.lposPendingApproval().subscribe(v => this.lposPending.set(v));

    const branchId = this.branch.activeBranchId();
    if (branchId == null) {
      this.dayLoaded.set(true);
      return;
    }
    this.dayService.currentDay(branchId).subscribe({
      next: d => { this.currentDay.set(d); this.dayLoaded.set(true); this.loadBranchMetrics(branchId); },
      error: () => { this.dayLoaded.set(true); this.loadBranchMetrics(branchId); },
    });
  }

  private loadBranchMetrics(branchId: string): void {
    const businessDate = this.currentDay()?.businessDate ?? this.today().toISOString().slice(0, 10);
    this.dashboard.todaysSales(branchId, businessDate).subscribe({
      next: v => this.todaysSales.set(v),
      error: () => this.todaysSales.set(null),
    });
    this.dashboard.stockAlertCount(branchId).subscribe({
      next: v => this.stockAlerts.set(v),
      error: () => this.stockAlerts.set(null),
    });
  }

  private loadArSummary(): void {
    const branchId = this.branch.activeBranchId();
    this.dashboard.arSummary(branchId).subscribe({
      next: summary => {
        this.arPermissionDenied.set(false);
        const outstandingNum = Number(summary.arOutstanding);
        this.arOutstanding.set(Number.isFinite(outstandingNum) ? outstandingNum : null);
        this.overdueInvoices.set(summary.overdueInvoices);
        this.openInvoices.set(summary.openInvoices);
        // The summary carries its own currency code (company default) — adopt
        // it so AR-tile formatting matches the backend's currency.
        if (summary.currencyCode) this.currencyCode.set(summary.currencyCode);
      },
      error: err => {
        if (err instanceof HttpErrorResponse && err.status === 403) {
          this.arPermissionDenied.set(true);
          this.arOutstanding.set(null);
          this.overdueInvoices.set(null);
          this.openInvoices.set(null);
        } else {
          // Other errors leave the tiles in the "—" state; don't crash.
          this.arOutstanding.set(null);
          this.overdueInvoices.set(null);
          this.openInvoices.set(null);
        }
      }
    });
  }

  private money(n: number | null, ccy: string): string {
    return n == null ? '—' : `${ccy} ${n.toLocaleString('en-US', { maximumFractionDigits: 0 })}`;
  }

  private count(n: number | null): string {
    return n == null ? '—' : n.toLocaleString('en-US');
  }
}

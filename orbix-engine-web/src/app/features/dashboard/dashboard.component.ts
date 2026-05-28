import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { DayService } from '../day/day.service';
import { BusinessDay } from '../day/day.models';
import { CompanyService } from '../admin/company/company.service';
import { DashboardService, DASHBOARD_LIVE, DashboardRollupResponse } from './dashboard.service';

/**
 * A single KPI tile descriptor. {@code testId} pins the wrapper for the
 * Slice F drill-through Playwright spec. When {@code link} is null OR
 * {@code denied} is true OR {@code value} is the "—" / "Permission required"
 * placeholder, the wrapper renders as an inert {@code <span>} with the
 * {@code data-testid} but no anchor (per QA's contract).
 */
interface Kpi {
  label: string;
  value: string;
  icon: string;
  tint: 'blue' | 'green' | 'amber' | 'rose';
  live: boolean;
  testId: string;
  link: string | null;
  queryParams: Record<string, string> | null;
  denied: boolean;
}

interface Alert {
  title: string;
  desc: string;
  tint: 'amber' | 'rose' | 'blue' | 'green';
  link: string;
  queryParams: Record<string, string>;
  cta: string;
  live: boolean;
  testId: string;
}

/**
 * Materialised inputs to {@link DashboardComponent.buildKpis} — collected
 * once from the signals so each per-tile builder reads pure data.
 */
interface KpiContext {
  ccy: string;
  arDenied: boolean;
  negDenied: boolean;
  branchId: string | null;
  businessDate: string;
  todaysSalesValue: number | null;
  stockAlertsValue: number | null;
  negativeStockNumeric: number | null;
  openInvoicesNumeric: number | null;
  arOutstandingNumeric: number | null;
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
        <div class="col-12 col-sm-6 col-lg-3" [attr.data-testid]="kpi.testId">
          @if (kpi.link !== null && !kpi.denied) {
            <a [routerLink]="kpi.link"
               [queryParams]="kpi.queryParams"
               class="card border-0 shadow-sm h-100 kpi-card kpi-card--link text-decoration-none"
               [attr.aria-label]="kpi.label + ': ' + kpi.value">
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
            </a>
          } @else {
            <span class="card border-0 shadow-sm h-100 kpi-card d-block">
              <span class="card-body p-4 d-block">
                <span class="d-flex justify-content-between align-items-start mb-3">
                  <span class="kpi-icon kpi-icon--{{ kpi.tint }}">
                    <i class="bi {{ kpi.icon }}"></i>
                  </span>
                  @if (!kpi.live) {
                    <span class="sample-pill" title="Depicted value — not yet wired to a live source">sample</span>
                  }
                </span>
                <span class="text-secondary small fw-semibold mb-1 text-uppercase d-block" style="letter-spacing:0.06em;">
                  {{ kpi.label }}
                </span>
                <span class="h4 fw-bold mb-0 text-dark d-block">{{ kpi.value }}</span>
              </span>
            </span>
          }
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
                <li class="alert-row" [class.alert-row--last]="last"
                    [attr.data-testid]="alert.testId">
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
                  <a [routerLink]="alert.link"
                     [queryParams]="alert.queryParams"
                     class="btn btn-sm btn-outline-secondary">{{ alert.cta }}</a>
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
    .kpi-card { transition: transform 0.15s ease, box-shadow 0.15s ease; color: inherit; }
    .kpi-card--link { cursor: pointer; }
    .kpi-card--link:hover {
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
  protected readonly negativeStockCount = signal<number | null>(null);
  protected readonly openInvoices = signal<number | null>(null);
  protected readonly arOutstanding = signal<number | null>(null);
  protected readonly overdueInvoices = signal<number | null>(null);
  protected readonly lposPending = signal<number | null>(null);
  /** AR tiles show "Permission required" when the caller lacks SALES.REPORT.AR_SUMMARY. */
  protected readonly arPermissionDenied = signal<boolean>(false);
  /** Negative-stock tile shows "Permission required" when the caller lacks STOCK.COUNT. */
  protected readonly negativeStockPermissionDenied = signal<boolean>(false);

  protected readonly kpis = computed<Kpi[]>(() => this.buildKpis());

  /**
   * Slice F — drill-through contract per Plan §3. Each tile carries an
   * {@code data-testid} (QA's contract) and resolves to either a link
   * (when the value is loaded AND the relevant permission is granted) or
   * an inert {@code <span>} (otherwise). The wrapper renders regardless
   * — only the inner anchor toggles, matching the spec's
   * {@code assertInertTile} pattern.
   */
  private buildKpis(): Kpi[] {
    const ctx = this.kpiContext();
    return [
      this.todaysSalesKpi(ctx),
      this.stockAlertsKpi(ctx),
      this.negativeStockKpi(ctx),
      this.openInvoicesKpi(ctx),
      this.arOutstandingKpi(ctx),
    ];
  }

  private kpiContext(): KpiContext {
    return {
      ccy: this.currencyCode(),
      arDenied: this.arPermissionDenied(),
      negDenied: this.negativeStockPermissionDenied(),
      branchId: this.activeBranchId(),
      businessDate: this.currentDay()?.businessDate
        ?? this.today().toISOString().slice(0, 10),
      todaysSalesValue: this.todaysSales(),
      stockAlertsValue: this.stockAlerts(),
      negativeStockNumeric: this.negativeStockCount(),
      openInvoicesNumeric: this.openInvoices(),
      arOutstandingNumeric: this.arOutstanding(),
    };
  }

  private todaysSalesKpi(c: KpiContext): Kpi {
    const branchId = c.branchId;
    const linked = c.todaysSalesValue !== null && branchId !== null;
    return {
      label: "Today's sales", value: this.money(c.todaysSalesValue, c.ccy),
      icon: 'bi-cash-coin', tint: 'green', live: DASHBOARD_LIVE.todaysSales,
      testId: 'kpi-todays-sales',
      link: linked ? '/reports/sales-daily' : null,
      queryParams: linked && branchId !== null
        ? { branchId, businessDate: c.businessDate }
        : null,
      denied: false,
    };
  }

  private stockAlertsKpi(c: KpiContext): Kpi {
    const linked = c.stockAlertsValue !== null;
    return {
      label: 'Stock alerts', value: this.count(c.stockAlertsValue),
      icon: 'bi-box-seam', tint: 'amber', live: DASHBOARD_LIVE.stockAlertCount,
      testId: 'kpi-stock-alerts',
      link: linked ? '/stock/balances' : null,
      queryParams: linked ? { belowReorderOnly: 'true' } : null,
      denied: false,
    };
  }

  private negativeStockKpi(c: KpiContext): Kpi {
    const linked = c.negativeStockNumeric !== null && c.negDenied === false;
    return {
      label: 'Negative stock',
      value: c.negDenied ? 'Permission required' : this.count(c.negativeStockNumeric),
      icon: 'bi-exclamation-octagon', tint: 'rose',
      live: DASHBOARD_LIVE.negativeStockCount, testId: 'kpi-negative-stock',
      link: linked ? '/reports/negative-stock' : null,
      queryParams: null,
      denied: c.negDenied,
    };
  }

  private openInvoicesKpi(c: KpiContext): Kpi {
    const linked = c.openInvoicesNumeric !== null && c.arDenied === false;
    return {
      label: 'Open invoices',
      value: c.arDenied ? 'Permission required' : this.count(c.openInvoicesNumeric),
      icon: 'bi-receipt', tint: 'blue', live: DASHBOARD_LIVE.openInvoiceCount,
      testId: 'kpi-open-invoices',
      link: linked ? '/sales/invoices' : null,
      queryParams: linked ? { status: 'OPEN' } : null,
      denied: c.arDenied,
    };
  }

  private arOutstandingKpi(c: KpiContext): Kpi {
    const linked = c.arOutstandingNumeric !== null && c.arDenied === false;
    return {
      label: 'AR outstanding',
      value: c.arDenied ? 'Permission required' : this.money(c.arOutstandingNumeric, c.ccy),
      icon: 'bi-people', tint: 'rose', live: DASHBOARD_LIVE.arOutstanding,
      testId: 'kpi-ar-outstanding',
      link: linked ? '/sales/invoices' : null,
      queryParams: linked ? { status: 'OPEN', sort: 'outstanding,desc' } : null,
      denied: c.arDenied,
    };
  }

  protected readonly alerts = computed<Alert[]>(() => {
    const out: Alert[] = [];
    this.pushIfPositive(out, this.stockAlerts(), n =>
      ({ tint: 'amber', title: `${n} SKU${n === 1 ? '' : 's'} at or below reorder level`,
        desc: 'Review balances and raise a purchase order.',
        link: '/stock/balances', queryParams: { belowReorderOnly: 'true' }, cta: 'Review',
        live: DASHBOARD_LIVE.stockAlertCount, testId: 'alert-stock-alerts' }));
    this.pushIfPositive(out, this.negativeStockCount(), n =>
      ({ tint: 'rose', title: `${n} item${n === 1 ? '' : 's'} in negative stock`,
        desc: 'OVERSELL moves have driven on-hand below zero — reconcile soon.',
        link: '/reports/negative-stock', queryParams: {}, cta: 'Review',
        live: DASHBOARD_LIVE.negativeStockCount, testId: 'alert-negative-stock' }));
    this.pushIfPositive(out, this.overdueInvoices(), n =>
      ({ tint: 'rose', title: `${n} invoice${n === 1 ? '' : 's'} past due`,
        desc: 'Chase outstanding receivables on the sales invoices view.',
        link: '/sales/invoices', queryParams: { status: 'OVERDUE' }, cta: 'Chase',
        live: DASHBOARD_LIVE.overdueInvoiceCount, testId: 'alert-overdue-invoices' }));
    this.pushIfPositive(out, this.lposPending(), n =>
      ({ tint: 'blue', title: `${n} LPO${n === 1 ? '' : 's'} awaiting approval`,
        desc: 'Procurement approvals are pending.',
        link: '/procurement/lpos', queryParams: { status: 'PENDING_APPROVAL' }, cta: 'Open',
        live: DASHBOARD_LIVE.lposPendingApproval, testId: 'alert-lpos-pending' }));
    return out;
  });

  private pushIfPositive(out: Alert[], n: number | null, build: (n: number) => Alert): void {
    if (n && n > 0) out.push(build(n));
  }

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

    // Business day still needs its own round-trip (read-side context used by
    // the day banner + the rollup's businessDate). The KPI tiles + alert
    // counts now come from a single rollup call below.
    const branchId = this.branch.activeBranchId();
    if (branchId == null) {
      this.dayLoaded.set(true);
      this.loadRollup(null, this.today().toISOString().slice(0, 10));
      return;
    }
    this.dayService.currentDay(branchId).subscribe({
      next: d => {
        this.currentDay.set(d);
        this.dayLoaded.set(true);
        const isoToday = this.today().toISOString().slice(0, 10);
        this.loadRollup(branchId, d?.businessDate ?? isoToday);
      },
      error: () => {
        this.dayLoaded.set(true);
        this.loadRollup(branchId, this.today().toISOString().slice(0, 10));
      },
    });
  }

  /**
   * Slice F — single round-trip replacing the four parallel per-fragment
   * calls (ar-summary + stock-negative + lpo-pending-count + sales-summary
   * + balances-derived stock-alert-count). Per-fragment authorisation:
   * fragments the caller lacks the perm for serialise as {@code null};
   * the component renders {@code null} as "Permission required" via the
   * existing {@code arPermissionDenied} / {@code negativeStockPermissionDenied}
   * signals.
   */
  private loadRollup(branchId: string | null, businessDate: string): void {
    this.dashboard.rollup(branchId, businessDate).subscribe({
      next: r => this.applyRollup(r),
      error: () => this.applyRollup(null),
    });
  }

  private applyRollup(r: DashboardRollupResponse | null): void {
    if (r == null) {
      // Whole-call failure — leave every tile in "—" state. Don't surface as
      // a permission error since we can't tell what the underlying cause was.
      this.todaysSales.set(null);
      this.stockAlerts.set(null);
      this.negativeStockCount.set(null);
      this.openInvoices.set(null);
      this.arOutstanding.set(null);
      this.overdueInvoices.set(null);
      this.lposPending.set(null);
      return;
    }

    if (r.currencyCode) this.currencyCode.set(r.currencyCode);

    // KPI section
    const kpi = r.kpi;
    if (kpi == null) {
      // Caller saw 403 on every per-fragment auth inside the KPI section.
      // Treat the AR / negative-stock tiles as permission-denied so the
      // existing UX still applies.
      this.arPermissionDenied.set(true);
      this.negativeStockPermissionDenied.set(true);
      this.todaysSales.set(null);
      this.stockAlerts.set(null);
      this.negativeStockCount.set(null);
      this.openInvoices.set(null);
      this.arOutstanding.set(null);
    } else {
      // Today's sales + stock-alerts have no permission gate today, so a null
      // value there means "no branch" / "data unavailable", not "permission
      // denied".
      this.todaysSales.set(kpi.todaysSales === null ? null : Number(kpi.todaysSales));
      this.stockAlerts.set(kpi.stockAlerts);

      // Negative-stock tile — null fragment means STOCK.COUNT denied.
      const negDenied = kpi.negativeStockCount === null;
      this.negativeStockPermissionDenied.set(negDenied);
      this.negativeStockCount.set(kpi.negativeStockCount);

      // AR tiles — both gated by SALES.REPORT.AR_SUMMARY. Either both come
      // back or neither (the same sub-call backs both).
      const arDenied = kpi.openInvoices === null && kpi.arOutstanding === null;
      this.arPermissionDenied.set(arDenied);
      this.openInvoices.set(kpi.openInvoices === null ? null : Number(kpi.openInvoices));
      this.arOutstanding.set(kpi.arOutstanding === null ? null : Number(kpi.arOutstanding));
    }

    // Alert section — counts drive the alert rows; null = perm-denied, the
    // alert simply does not surface (the spec asserts the row's testid is
    // absent in that case).
    const overdue = r.alerts?.overdueInvoiceCount;
    this.overdueInvoices.set(overdue == null ? null : Number(overdue));
    const lpos = r.alerts?.lposPendingApproval;
    this.lposPending.set(lpos == null ? null : Number(lpos));
  }

  private money(n: number | null, ccy: string): string {
    return n == null ? '—' : `${ccy} ${n.toLocaleString('en-US', { maximumFractionDigits: 0 })}`;
  }

  private count(n: number | null): string {
    return n == null ? '—' : n.toLocaleString('en-US');
  }
}

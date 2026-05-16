import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { DayService } from '../day/day.service';
import { BusinessDay } from '../day/day.models';

interface Kpi {
  label: string;
  value: string;
  delta?: string;
  deltaPositive?: boolean;
  icon: string;
  tint: 'blue' | 'green' | 'amber' | 'rose';
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
        <button type="button" class="btn btn-outline-secondary d-inline-flex align-items-center gap-2">
          <i class="bi bi-printer"></i><span class="d-none d-sm-inline">Print Z-report</span>
        </button>
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
                @if (kpi.delta) {
                  <span class="badge rounded-pill small"
                        [class.text-bg-success-subtle]="kpi.deltaPositive"
                        [class.text-bg-danger-subtle]="!kpi.deltaPositive">
                    <i class="bi" [class.bi-arrow-up-right]="kpi.deltaPositive"
                                  [class.bi-arrow-down-right]="!kpi.deltaPositive"></i>
                    {{ kpi.delta }}
                  </span>
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
              <a routerLink="/stock" class="small text-decoration-none">View all</a>
            </div>
            <ul class="list-unstyled mb-0">
              <li class="alert-row">
                <span class="alert-row__dot alert-row__dot--amber"></span>
                <div class="flex-grow-1">
                  <p class="mb-0 fw-semibold text-dark small">12 SKUs below reorder level</p>
                  <p class="mb-0 small text-secondary">Run a stock-take in Main Warehouse.</p>
                </div>
                <a routerLink="/stock" class="btn btn-sm btn-outline-secondary">Review</a>
              </li>
              <li class="alert-row">
                <span class="alert-row__dot alert-row__dot--rose"></span>
                <div class="flex-grow-1">
                  <p class="mb-0 fw-semibold text-dark small">3 invoices over 60 days past due</p>
                  <p class="mb-0 small text-secondary">Send statements via Debt &rsaquo; Customer.</p>
                </div>
                <a routerLink="/debt" class="btn btn-sm btn-outline-secondary">Chase</a>
              </li>
              <li class="alert-row">
                <span class="alert-row__dot alert-row__dot--blue"></span>
                <div class="flex-grow-1">
                  <p class="mb-0 fw-semibold text-dark small">2 GRNs awaiting approval</p>
                  <p class="mb-0 small text-secondary">Procurement queue is filling up.</p>
                </div>
                <a routerLink="/procurement" class="btn btn-sm btn-outline-secondary">Open</a>
              </li>
              <li class="alert-row alert-row--last">
                <span class="alert-row__dot alert-row__dot--green"></span>
                <div class="flex-grow-1">
                  <p class="mb-0 fw-semibold text-dark small">Backups healthy</p>
                  <p class="mb-0 small text-secondary">Last snapshot {{ lastBackup() }}.</p>
                </div>
              </li>
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

    .text-bg-success-subtle { background: #d1fae5; color: #047857; }
    .text-bg-danger-subtle  { background: #fee2e2; color: #b91c1c; }

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

  protected readonly today = signal(new Date());

  protected readonly currentDay = signal<BusinessDay | null>(null);
  protected readonly dayLoaded = signal(false);

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

  protected readonly kpis = signal<Kpi[]>([
    { label: "Today's sales",   value: 'KES 0',  delta: '0%',   deltaPositive: true,  icon: 'bi-cash-coin',       tint: 'green' },
    { label: 'Open invoices',   value: '—',      delta: undefined,                    icon: 'bi-receipt',         tint: 'blue'  },
    { label: 'Stock alerts',    value: '—',      delta: undefined,                    icon: 'bi-box-seam',        tint: 'amber' },
    { label: 'AR outstanding',  value: 'KES 0',  delta: undefined,                    icon: 'bi-people',          tint: 'rose'  },
  ]);

  protected readonly quickActions: QuickAction[] = [
    { label: 'New sale',       icon: 'bi-cart-plus',  link: '/sales',       description: 'Ring up a customer order' },
    { label: 'Receive stock',  icon: 'bi-truck',      link: '/procurement', description: 'Log a GRN from a supplier' },
    { label: 'Add product',    icon: 'bi-tag',        link: '/catalog',     description: 'Create or update an item' },
    { label: 'New customer',   icon: 'bi-person-plus',link: '/party',       description: 'Add a party to the book' },
    { label: 'Stock-take',     icon: 'bi-clipboard2-check', link: '/stock', description: 'Reconcile shelf counts' },
    { label: 'Reports',        icon: 'bi-graph-up',   link: '/reports',     description: 'Sales, AR, AP, profit' },
  ];

  protected readonly lastBackup = signal('a few minutes ago');

  ngOnInit(): void {
    const branchId = this.branch.activeBranchId();
    if (branchId == null) {
      this.dayLoaded.set(true);
      return;
    }
    this.dayService.currentDay(branchId).subscribe({
      next: d => {
        this.currentDay.set(d);
        this.dayLoaded.set(true);
      },
      error: () => this.dayLoaded.set(true),
    });
  }
}

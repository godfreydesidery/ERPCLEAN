import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { AccessibleBranch, BranchService } from '../core/branch/branch.service';

interface NavLink {
  readonly label: string;
  readonly route: string;
  readonly icon: string;
  readonly tooltip: string;
}

interface NavGroup {
  readonly label: string;
  readonly items: readonly NavLink[];
}

@Component({
  selector: 'orbix-shell',
  standalone: true,
  imports: [NgClass, RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <div class="shell-layout">

      <!-- Top bar -->
      <header class="topbar">
        <button class="btn btn-link topbar-toggle d-lg-none"
                type="button"
                (click)="sidebarOpen.set(!sidebarOpen())"
                [attr.aria-label]="sidebarOpen() ? 'Close menu' : 'Open menu'"
                [attr.aria-expanded]="sidebarOpen()">
          <i class="bi" [class.bi-list]="!sidebarOpen()" [class.bi-x-lg]="sidebarOpen()"></i>
        </button>

        <a class="topbar-brand" routerLink="/dashboard" (click)="closeSidebar()">
          <span class="brand-mark-sm">O</span>
          <span class="brand-word d-none d-sm-inline">ORBIX ERP</span>
        </a>

        <div class="topbar-spacer"></div>

        @if (branches().length > 0) {
          <div class="branch-chip-wrap">
            <button type="button"
                    class="branch-chip"
                    [class.is-switchable]="branches().length > 1"
                    [class.is-open]="branchMenuOpen()"
                    [disabled]="switching() || branches().length === 1"
                    (click)="$event.stopPropagation(); branchMenuOpen.set(!branchMenuOpen())"
                    [attr.aria-label]="'Active branch: ' + (selectedBranch()?.name ?? 'none')"
                    [attr.aria-expanded]="branchMenuOpen()">
              <span class="branch-chip__icon"><i class="bi bi-building"></i></span>
              <span class="branch-chip__body">
                <span class="branch-chip__eyebrow">Branch</span>
                <span class="branch-chip__name">
                  {{ selectedBranch()?.name ?? 'Select…' }}
                  @if (selectedBranch()?.code) {
                    <span class="branch-chip__code d-none d-sm-inline">{{ selectedBranch()!.code }}</span>
                  }
                </span>
              </span>
              @if (branches().length > 1) {
                <i class="bi bi-chevron-down branch-chip__chev"
                   [class.flip]="branchMenuOpen()"></i>
              }
            </button>
            @if (branchMenuOpen()) {
              <div class="branch-menu shadow" (click)="$event.stopPropagation()">
                <div class="branch-menu__header">Switch branch</div>
                @for (branch of branches(); track branch.id) {
                  <button type="button" class="branch-menu__item"
                          [class.is-active]="branch.id === selectedBranchId()"
                          [disabled]="switching()"
                          (click)="onBranchPick(branch.id)">
                    <span class="branch-menu__dot"></span>
                    <span class="flex-grow-1 text-truncate">
                      <span class="fw-semibold">{{ branch.name }}</span>
                      <span class="small text-muted ms-1 font-monospace">{{ branch.code }}</span>
                    </span>
                    @if (branch.id === selectedBranchId()) {
                      <i class="bi bi-check2 text-primary"></i>
                    }
                  </button>
                }
              </div>
            }
          </div>
        }

        @if (user()) {
          <div class="topbar-user">
            <button class="btn btn-link user-button" type="button"
                    (click)="$event.stopPropagation(); userMenuOpen.set(!userMenuOpen())"
                    [attr.aria-expanded]="userMenuOpen()">
              <span class="user-avatar">{{ initials() }}</span>
              <span class="user-name d-none d-sm-inline">{{ user()!.displayName }}</span>
              <i class="bi bi-chevron-down ms-1 small text-muted d-none d-sm-inline"></i>
            </button>
            @if (userMenuOpen()) {
              <div class="user-menu shadow-sm" (click)="$event.stopPropagation()">
                <div class="user-menu__header px-3 py-2">
                  <div class="fw-semibold small text-dark">{{ user()!.displayName }}</div>
                  <div class="text-muted small">&#64;{{ user()!.username }}</div>
                </div>
                <hr class="my-1">
                <button class="user-menu__item" type="button"
                        (click)="userMenuOpen.set(false); goChangePassword()">
                  <i class="bi bi-key me-2"></i>Change password
                </button>
                <button class="user-menu__item user-menu__item--danger" type="button"
                        (click)="userMenuOpen.set(false); logout()">
                  <i class="bi bi-box-arrow-right me-2"></i>Sign out
                </button>
              </div>
            }
          </div>

          <!-- Always-visible direct sign-out fallback. Belt and braces in case
               the dropdown is hard to discover or fails. -->
          <button type="button" class="btn btn-link logout-btn d-none d-md-inline-flex"
                  (click)="logout()" title="Sign out">
            <i class="bi bi-box-arrow-right"></i>
          </button>
        }
      </header>

      <!-- Sidebar (dark, off-canvas on mobile) -->
      <aside class="sidebar" [ngClass]="{ 'sidebar-open': sidebarOpen() }">
        <nav class="sidebar-nav">
          @for (group of nav; track group.label) {
            <div class="nav-group">
              <div class="nav-group-label">{{ group.label }}</div>
              @for (item of group.items; track item.route) {
                <a class="nav-item"
                   [routerLink]="item.route"
                   routerLinkActive="active"
                   [title]="item.tooltip"
                   (click)="closeSidebar()">
                  <i class="bi {{ item.icon }} nav-item-icon"></i>
                  <span>{{ item.label }}</span>
                </a>
              }
            </div>
          }
        </nav>
      </aside>

      <!-- Mobile backdrop -->
      @if (sidebarOpen()) {
        <div class="sidebar-backdrop d-lg-none" (click)="closeSidebar()"></div>
      }

      <!-- Main content -->
      <main class="shell-main">
        <div class="shell-main-inner">
          <router-outlet></router-outlet>
        </div>
      </main>
    </div>
  `,
  styles: [`
    :host {
      --sb-width: 248px;
      --tb-height: 56px;
      --sb-bg: #0d2a5b;
      --sb-bg-hover: rgba(255, 255, 255, 0.08);
      --sb-bg-active: rgba(255, 255, 255, 0.14);
      --sb-fg: rgba(255, 255, 255, 0.85);
      --sb-fg-muted: rgba(255, 255, 255, 0.55);
      --sb-accent: #4178d9;
      --content-bg: #f5f7fb;
      display: block;
    }

    .shell-layout {
      min-height: 100vh;
      min-height: 100dvh;
      background: var(--content-bg);
    }

    /* ------- Topbar ------- */
    .topbar {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      height: var(--tb-height);
      background: #fff;
      border-bottom: 1px solid #e6e9ee;
      display: flex;
      align-items: center;
      padding: 0 1rem;
      padding-top: env(safe-area-inset-top);
      z-index: 1030;
      gap: 0.5rem;
    }
    .topbar-toggle {
      padding: 0.25rem 0.5rem;
      font-size: 1.25rem;
      color: #1f2933;
      text-decoration: none;
    }
    .topbar-toggle:hover, .topbar-toggle:focus { color: var(--sb-accent); }

    .topbar-brand {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      text-decoration: none;
      color: #1a4fb5;
      font-weight: 700;
      letter-spacing: 0.1em;
      font-size: 0.95rem;
    }
    .topbar-brand:hover { color: #0d2a5b; }

    .brand-mark-sm {
      width: 32px;
      height: 32px;
      border-radius: 8px;
      background: linear-gradient(135deg, #1a4fb5, #4178d9);
      color: #fff;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 1.1rem;
      letter-spacing: -1px;
      box-shadow: 0 2px 8px rgba(26, 79, 181, 0.3);
    }

    .topbar-spacer { flex: 1; }

    /* ------- Branch chip + dropdown ------- */
    .branch-chip-wrap { position: relative; }
    .branch-chip {
      display: inline-flex;
      align-items: center;
      gap: 0.625rem;
      padding: 0.3rem 0.75rem 0.3rem 0.3rem;
      background: #f1f5fb;
      border: 1px solid #dde6f3;
      border-radius: 999px;
      color: #0d2a5b;
      font-size: 0.85rem;
      line-height: 1;
      cursor: pointer;
      transition: border-color 0.15s ease, background 0.15s ease, box-shadow 0.15s ease;
    }
    .branch-chip:hover.is-switchable {
      background: #e8eff9;
      border-color: #b9c8df;
    }
    .branch-chip.is-open {
      border-color: #1a4fb5;
      background: #fff;
      box-shadow: 0 0 0 0.2rem rgba(26, 79, 181, 0.12);
    }
    .branch-chip:disabled { cursor: default; opacity: 0.85; }

    .branch-chip__icon {
      width: 30px; height: 30px; border-radius: 50%;
      display: inline-flex; align-items: center; justify-content: center;
      background: linear-gradient(135deg, #1a4fb5, #4178d9);
      color: #fff;
      font-size: 0.9rem;
      flex-shrink: 0;
    }
    .branch-chip__body {
      display: inline-flex; flex-direction: column;
      align-items: flex-start; justify-content: center;
      line-height: 1.1;
      min-width: 0;
    }
    .branch-chip__eyebrow {
      font-size: 0.62rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: #6b7d96;
    }
    .branch-chip__name {
      font-size: 0.92rem;
      font-weight: 600;
      max-width: 200px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .branch-chip__code {
      margin-left: 0.4rem;
      font-family: ui-monospace, SFMono-Regular, monospace;
      font-size: 0.72rem;
      font-weight: 600;
      color: #6b7d96;
    }
    .branch-chip__chev {
      font-size: 0.85rem;
      color: #6b7d96;
      transition: transform 0.15s ease;
    }
    .branch-chip__chev.flip { transform: rotate(180deg); }

    .branch-menu {
      position: absolute;
      top: calc(100% + 0.5rem);
      left: 0;
      min-width: 280px;
      max-width: 360px;
      max-height: 60vh;
      overflow-y: auto;
      background: #fff;
      border: 1px solid #dde6f3;
      border-radius: 12px;
      padding: 0.5rem 0;
      z-index: 1040;
    }
    .branch-menu__header {
      padding: 0.25rem 1rem 0.5rem;
      font-size: 0.7rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: #6b7d96;
      border-bottom: 1px solid #f3f4f6;
      margin-bottom: 0.25rem;
    }
    .branch-menu__item {
      width: 100%;
      display: flex;
      align-items: center;
      gap: 0.625rem;
      padding: 0.55rem 1rem;
      background: transparent;
      border: none;
      text-align: left;
      font-size: 0.9rem;
      color: #1f2933;
      cursor: pointer;
    }
    .branch-menu__item:hover { background: #f4f6fa; }
    .branch-menu__item.is-active { background: #eef4ff; }
    .branch-menu__item:disabled { opacity: 0.6; cursor: not-allowed; }
    .branch-menu__dot {
      width: 8px; height: 8px; border-radius: 50%;
      background: #cbd5e1;
      flex-shrink: 0;
    }
    .branch-menu__item.is-active .branch-menu__dot { background: #1a4fb5; }

    @media (max-width: 575.98px) {
      .branch-chip__eyebrow { display: none; }
      .branch-chip__name { font-size: 0.85rem; max-width: 120px; }
    }

    @media (prefers-reduced-motion: reduce) {
      .branch-chip, .branch-chip__chev { transition: none; }
    }

    .topbar-user .user-button {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.25rem 0.5rem;
      text-decoration: none;
      color: #1f2933;
      border-radius: 0.5rem;
    }
    .topbar-user .user-button:hover { background: #f0f3f7; }
    .user-avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: #e8eef9;
      color: #1a4fb5;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-weight: 600;
      font-size: 0.85rem;
    }
    .user-name { font-size: 0.9rem; font-weight: 500; }

    .topbar-user { position: relative; }
    .user-menu {
      position: absolute;
      top: calc(100% + 0.5rem);
      right: 0;
      min-width: 240px;
      background: #fff;
      border: 1px solid #e6e9ee;
      border-radius: 10px;
      padding: 0.5rem 0;
      z-index: 1040;
    }
    .user-menu__header { border-bottom: 1px solid #f3f4f6; padding-bottom: 0.5rem !important; }
    .user-menu__item {
      width: 100%;
      display: flex;
      align-items: center;
      padding: 0.5rem 1rem;
      background: transparent;
      border: none;
      text-align: left;
      font-size: 0.9rem;
      color: #1f2933;
      cursor: pointer;
    }
    .user-menu__item:hover { background: #f4f6fa; }
    .user-menu__item--danger { color: #b91c1c; }
    .user-menu__item--danger:hover { background: #fee2e2; }

    .logout-btn {
      padding: 0.4rem 0.6rem;
      color: #b91c1c;
      border-radius: 0.5rem;
      text-decoration: none;
    }
    .logout-btn:hover, .logout-btn:focus { background: #fee2e2; color: #991b1b; }

    /* ------- Sidebar ------- */
    .sidebar {
      position: fixed;
      top: var(--tb-height);
      left: 0;
      bottom: 0;
      width: var(--sb-width);
      background: var(--sb-bg);
      color: var(--sb-fg);
      overflow-y: auto;
      z-index: 1029;
      padding: 1rem 0 calc(env(safe-area-inset-bottom) + 1rem);
      transition: transform 0.22s ease;
      box-shadow: 1px 0 0 rgba(0, 0, 0, 0.08);
    }
    .sidebar-nav { display: flex; flex-direction: column; gap: 1rem; }
    .nav-group { display: flex; flex-direction: column; }
    .nav-group-label {
      padding: 0.25rem 1.25rem;
      font-size: 0.7rem;
      font-weight: 600;
      letter-spacing: 0.12em;
      text-transform: uppercase;
      color: var(--sb-fg-muted);
    }
    .nav-item {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.6rem 1.25rem;
      color: var(--sb-fg);
      text-decoration: none;
      font-size: 0.92rem;
      border-left: 3px solid transparent;
      transition: background 0.12s ease, color 0.12s ease, border-color 0.12s ease;
    }
    .nav-item-icon { font-size: 1.05rem; opacity: 0.9; }
    .nav-item:hover { background: var(--sb-bg-hover); color: #fff; }
    .nav-item.active {
      background: var(--sb-bg-active);
      border-left-color: var(--sb-accent);
      color: #fff;
      font-weight: 600;
    }
    .nav-item.active .nav-item-icon { opacity: 1; }

    /* ------- Main content ------- */
    .shell-main {
      margin-top: var(--tb-height);
      margin-left: var(--sb-width);
      min-height: calc(100dvh - var(--tb-height));
    }
    .shell-main-inner {
      padding: 1.5rem;
      padding-bottom: calc(env(safe-area-inset-bottom) + 1.5rem);
      max-width: 1400px;
      margin: 0 auto;
    }

    /* ------- Mobile breakpoint ------- */
    @media (max-width: 991.98px) {
      .sidebar { transform: translateX(-100%); }
      .sidebar-open { transform: translateX(0); }
      .shell-main { margin-left: 0; }
      .shell-main-inner { padding: 1rem; }
    }

    .sidebar-backdrop {
      position: fixed;
      top: var(--tb-height);
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(13, 42, 91, 0.45);
      z-index: 1028;
      animation: fadeIn 0.18s ease;
    }
    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    @media (prefers-reduced-motion: reduce) {
      .sidebar { transition: none; }
      .sidebar-backdrop { animation: none; }
    }
  `]
})
export class ShellComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly branchService = inject(BranchService);
  private readonly router = inject(Router);

  readonly user = this.auth.currentUser;
  readonly branches = signal<AccessibleBranch[]>([]);
  readonly switching = signal(false);
  readonly sidebarOpen = signal(false);
  readonly userMenuOpen = signal(false);
  readonly branchMenuOpen = signal(false);

  readonly selectedBranchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  readonly selectedBranch = computed(() => {
    const id = this.selectedBranchId();
    return id === null ? null : this.branches().find(b => b.id === id) ?? null;
  });

  readonly initials = computed(() => {
    const name = this.auth.currentUser()?.displayName ?? '';
    const parts = name.trim().split(/\s+/).filter(p => p.length > 0);
    if (parts.length === 0) return '?';
    const first = parts[0].charAt(0);
    if (parts.length === 1) return first.toUpperCase();
    const last = parts.at(-1) ?? '';
    return (first + last.charAt(0)).toUpperCase();
  });

  readonly nav: readonly NavGroup[] = [
    {
      label: 'Overview',
      items: [
        {
          label: 'Dashboard', route: '/dashboard', icon: 'bi-speedometer2',
          tooltip: "Today's KPIs (sales, cash, stock alerts) and recent activity for the branch you're working in. Your starting point each morning."
        }
      ]
    },
    {
      label: 'Operations',
      items: [
        {
          label: 'Business day', route: '/day', icon: 'bi-calendar-check',
          tooltip: 'Open the trading day for a branch before any postings are allowed, and close it at end of day to lock in stock, cash, and sales balances. The on-call manager owns this.'
        },
        {
          label: 'Catalog', route: '/catalog', icon: 'bi-box-seam',
          tooltip: 'Define what you sell or move through stock — items, the group tree, units of measure, VAT rules, price lists, and barcodes. Set up once, referenced everywhere else.'
        },
        {
          label: 'Parties', route: '/party', icon: 'bi-people',
          tooltip: 'Everyone the business deals with: customers, suppliers, employees, sales agents. One person can wear several hats — all roles attach to the same party record.'
        },
        {
          label: 'Stock', route: '/stock', icon: 'bi-clipboard-data',
          tooltip: 'On-hand quantities per item per branch, stock counts to reconcile against reality, transfers between branches, batch tracking for expiry, and one-off adjustments.'
        },
        {
          label: 'Production', route: '/production', icon: 'bi-gear',
          tooltip: 'Recipes (BOMs), production batches that consume inputs and yield outputs, unit conversions, and wastage. Used by bakeries, kitchens, and any operation that turns inputs into a different sellable item.'
        }
      ]
    },
    {
      label: 'Commerce',
      items: [
        {
          label: 'Sales', route: '/sales', icon: 'bi-cart-check',
          tooltip: 'Quote → invoice → receipt → allocation, plus customer returns and packing lists. Back-office sales — POS sales live in the till app, not here.'
        },
        {
          label: 'Procurement', route: '/procurement', icon: 'bi-truck',
          tooltip: 'LPOs out to suppliers, GRNs when goods arrive, supplier invoices matched against GRNs, and payments out. Builds the AP side of the ledger.'
        }
      ]
    },
    {
      label: 'Finance',
      items: [
        {
          label: 'Debt', route: '/debt', icon: 'bi-credit-card',
          tooltip: 'Ageing of what customers owe you (receivables) and what you owe suppliers (payables), bucketed by current / 30d / 60d / 90d+. Drives collections and payment-run decisions.'
        },
        {
          label: 'Reports', route: '/reports', icon: 'bi-bar-chart-line',
          tooltip: 'Pre-built reports across stock, sales, production, gift cards, VAT, and customer statements. Run on demand or scheduled to email on a cadence.'
        }
      ]
    },
    {
      label: 'Settings',
      items: [
        {
          label: 'Admin', route: '/admin', icon: 'bi-sliders',
          tooltip: 'System configuration — users, roles & permissions, branches & sections, currencies, FX rates, POS tills, and delivery routes. Mostly one-time setup with occasional changes.'
        }
      ]
    }
  ] as const;

  ngOnInit(): void {
    this.branchService.listBranches().subscribe({
      next: branches => this.branches.set(branches),
      error: () => this.branches.set([])
    });
  }

  onBranchPick(branchId: number): void {
    if (!Number.isFinite(branchId) || branchId === this.selectedBranchId()) {
      this.branchMenuOpen.set(false);
      return;
    }
    this.switching.set(true);
    this.branchMenuOpen.set(false);
    this.branchService.setActiveBranch(branchId).subscribe({
      // Reload so every screen re-fetches its data scoped to the new branch.
      next: () => globalThis.location.reload(),
      error: () => this.switching.set(false)
    });
  }

  closeSidebar(): void {
    if (this.sidebarOpen()) this.sidebarOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeSidebar();
    this.userMenuOpen.set(false);
    this.branchMenuOpen.set(false);
  }

  // Close any open menu on a click outside it. Menus stop propagation on
  // their own click so this only fires on outside clicks.
  @HostListener('document:click')
  onDocumentClick(): void {
    if (this.userMenuOpen()) this.userMenuOpen.set(false);
    if (this.branchMenuOpen()) this.branchMenuOpen.set(false);
  }

  goChangePassword(): void {
    void this.router.navigate(['/change-password']);
  }

  logout(): void {
    this.auth.logout().subscribe({
      complete: () => void this.router.navigate(['/login'])
    });
  }
}

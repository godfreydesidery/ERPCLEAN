import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { AccessibleBranch, BranchService } from '../core/branch/branch.service';

interface NavLink {
  readonly label: string;
  readonly route: string;
  readonly icon: string;
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

        @if (branches().length > 1) {
          <div class="topbar-branch d-none d-md-flex">
            <i class="bi bi-building me-2 text-secondary"></i>
            <select class="form-select form-select-sm branch-select"
                    [value]="selectedBranchId() ?? ''"
                    (change)="onBranchChange($event)"
                    [disabled]="switching()">
              @for (branch of branches(); track branch.id) {
                <option [value]="branch.id">{{ branch.name }}</option>
              }
            </select>
          </div>
        }

        @if (user()) {
          <div class="dropdown topbar-user">
            <button class="btn btn-link user-button" type="button"
                    data-bs-toggle="dropdown"
                    aria-expanded="false">
              <span class="user-avatar">{{ initials() }}</span>
              <span class="user-name d-none d-sm-inline">{{ user()!.displayName }}</span>
              <i class="bi bi-chevron-down ms-1 small text-muted d-none d-sm-inline"></i>
            </button>
            <ul class="dropdown-menu dropdown-menu-end shadow-sm">
              <li class="px-3 py-2">
                <div class="fw-semibold small">{{ user()!.displayName }}</div>
                <div class="text-muted small">{{ '@' + user()!.username }}</div>
              </li>
              @if (branches().length > 1) {
                <li><hr class="dropdown-divider d-md-none"></li>
                <li class="px-3 pb-2 d-md-none">
                  <label class="form-label small text-muted mb-1">Active branch</label>
                  <select class="form-select form-select-sm"
                          [value]="selectedBranchId() ?? ''"
                          (change)="onBranchChange($event)"
                          [disabled]="switching()">
                    @for (branch of branches(); track branch.id) {
                      <option [value]="branch.id">{{ branch.name }}</option>
                    }
                  </select>
                </li>
              }
              <li><hr class="dropdown-divider"></li>
              <li>
                <button class="dropdown-item" type="button" (click)="logout()">
                  <i class="bi bi-box-arrow-right me-2"></i>Sign out
                </button>
              </li>
            </ul>
          </div>
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

    .topbar-branch { align-items: center; }
    .branch-select {
      min-width: 180px;
      border-color: #dde1e7;
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

    .topbar-user .dropdown-menu { min-width: 240px; border: 1px solid #e6e9ee; }
    .topbar-user .dropdown-item { font-size: 0.9rem; }

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

  readonly selectedBranchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

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
        { label: 'Dashboard', route: '/dashboard', icon: 'bi-speedometer2' }
      ]
    },
    {
      label: 'Operations',
      items: [
        { label: 'Business day', route: '/day', icon: 'bi-calendar-check' },
        { label: 'Catalog', route: '/catalog', icon: 'bi-box-seam' },
        { label: 'Parties', route: '/party', icon: 'bi-people' },
        { label: 'Stock', route: '/stock', icon: 'bi-clipboard-data' },
        { label: 'Production', route: '/production', icon: 'bi-gear' }
      ]
    },
    {
      label: 'Commerce',
      items: [
        { label: 'Sales', route: '/sales', icon: 'bi-cart-check' },
        { label: 'Procurement', route: '/procurement', icon: 'bi-truck' }
      ]
    },
    {
      label: 'Finance',
      items: [
        { label: 'Debt', route: '/debt', icon: 'bi-credit-card' },
        { label: 'Reports', route: '/reports', icon: 'bi-bar-chart-line' }
      ]
    },
    {
      label: 'Settings',
      items: [
        { label: 'Admin', route: '/admin', icon: 'bi-sliders' }
      ]
    }
  ] as const;

  ngOnInit(): void {
    this.branchService.listBranches().subscribe({
      next: branches => this.branches.set(branches),
      error: () => this.branches.set([])
    });
  }

  onBranchChange(event: Event): void {
    const branchId = Number((event.target as HTMLSelectElement).value);
    if (!Number.isFinite(branchId) || branchId === this.selectedBranchId()) return;
    this.switching.set(true);
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
  }

  logout(): void {
    this.auth.logout().subscribe({
      complete: () => void this.router.navigate(['/login'])
    });
  }
}

import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { AccessibleBranch, BranchService } from '../core/branch/branch.service';

@Component({
  selector: 'orbix-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <div class="d-flex" style="min-height: 100vh">
      <aside class="bg-light border-end p-3 d-flex flex-column" style="width: 240px">
        <h1 class="h5 mb-3">Orbix Engine</h1>

        @if (branches().length > 1) {
          <div class="mb-3">
            <label class="form-label small text-muted mb-1">Active branch</label>
            <select class="form-select form-select-sm" [value]="selectedBranchId() ?? ''"
                    (change)="onBranchChange($event)" [disabled]="switching()">
              @for (branch of branches(); track branch.id) {
                <option [value]="branch.id">{{ branch.name }}</option>
              }
            </select>
          </div>
        }

        <nav class="nav flex-column flex-grow-1">
          <a class="nav-link" routerLink="/dashboard" routerLinkActive="active">Dashboard</a>
          <a class="nav-link" routerLink="/catalog"     routerLinkActive="active">Catalog</a>
          <a class="nav-link" routerLink="/party"       routerLinkActive="active">Parties</a>
          <a class="nav-link" routerLink="/day"         routerLinkActive="active">Business day</a>
          <a class="nav-link" routerLink="/sales"       routerLinkActive="active">Sales</a>
          <a class="nav-link" routerLink="/procurement" routerLinkActive="active">Procurement</a>
          <a class="nav-link" routerLink="/stock"       routerLinkActive="active">Stock</a>
          <a class="nav-link" routerLink="/production"  routerLinkActive="active">Production</a>
          <a class="nav-link" routerLink="/debt"        routerLinkActive="active">Debt</a>
          <a class="nav-link" routerLink="/reports"     routerLinkActive="active">Reports</a>
          <a class="nav-link" routerLink="/admin"       routerLinkActive="active">Admin</a>
        </nav>
        @if (user()) {
          <div class="border-top pt-3 mt-3 small">
            <div class="fw-semibold">{{ user()!.displayName }}</div>
            <div class="text-muted">{{ '@' + user()!.username }}</div>
            <button class="btn btn-sm btn-outline-secondary w-100 mt-2" (click)="logout()">Sign out</button>
          </div>
        }
      </aside>
      <section class="flex-grow-1 p-4">
        <router-outlet></router-outlet>
      </section>
    </div>
  `
})
export class ShellComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly branchService = inject(BranchService);
  private readonly router = inject(Router);

  readonly user = this.auth.currentUser;
  readonly branches = signal<AccessibleBranch[]>([]);
  readonly switching = signal(false);

  readonly selectedBranchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

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

  logout(): void {
    this.auth.logout().subscribe({
      complete: () => void this.router.navigate(['/login'])
    });
  }
}

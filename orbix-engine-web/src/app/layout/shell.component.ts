import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'orbix-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <div class="d-flex" style="min-height: 100vh">
      <aside class="bg-light border-end p-3" style="width: 240px">
        <h1 class="h5 mb-4">Orbix Engine</h1>
        <nav class="nav flex-column">
          <a class="nav-link" routerLink="/dashboard" routerLinkActive="active">Dashboard</a>
          <a class="nav-link" routerLink="/catalog" routerLinkActive="active">Catalog</a>
          <a class="nav-link" routerLink="/sales" routerLinkActive="active">Sales</a>
          <a class="nav-link" routerLink="/procurement" routerLinkActive="active">Procurement</a>
          <a class="nav-link" routerLink="/stock" routerLinkActive="active">Stock</a>
          <a class="nav-link" routerLink="/production" routerLinkActive="active">Production</a>
          <a class="nav-link" routerLink="/debt" routerLinkActive="active">Debt</a>
          <a class="nav-link" routerLink="/reports" routerLinkActive="active">Reports</a>
          <a class="nav-link" routerLink="/admin" routerLinkActive="active">Admin</a>
        </nav>
      </aside>
      <section class="flex-grow-1 p-4">
        <router-outlet></router-outlet>
      </section>
    </div>
  `
})
export class ShellComponent {}

import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

/**
 * Top-level routes. Each business area lazy-loads its own module of routes.
 * Mirrors the modules in PRD §5 / USER-STORIES.md epics.
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent)
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then(m => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'catalog',
        loadChildren: () => import('./features/catalog/catalog.routes').then(m => m.routes)
      },
      {
        path: 'sales',
        loadChildren: () => import('./features/sales/sales.routes').then(m => m.routes)
      },
      {
        path: 'procurement',
        loadChildren: () => import('./features/procurement/procurement.routes').then(m => m.routes)
      },
      {
        path: 'stock',
        loadChildren: () => import('./features/stock/stock.routes').then(m => m.routes)
      },
      {
        path: 'production',
        loadChildren: () => import('./features/production/production.routes').then(m => m.routes)
      },
      {
        path: 'debt',
        loadChildren: () => import('./features/debt/debt.routes').then(m => m.routes)
      },
      {
        path: 'reports',
        loadChildren: () => import('./features/reports/reports.routes').then(m => m.routes)
      },
      {
        path: 'admin',
        loadChildren: () => import('./features/admin/admin.routes').then(m => m.routes)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];

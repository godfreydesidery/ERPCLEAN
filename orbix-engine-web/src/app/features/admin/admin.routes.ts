import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./admin.component').then(m => m.AdminComponent)
  },
  {
    path: 'users',
    loadComponent: () => import('./users/user-admin.component').then(m => m.UserAdminComponent)
  },
  {
    path: 'users/:uid',
    loadComponent: () => import('./users/user-detail.component').then(m => m.UserDetailComponent)
  },
  {
    path: 'roles',
    loadComponent: () => import('./roles/role-admin.component').then(m => m.RoleAdminComponent)
  },
  {
    path: 'audit',
    loadComponent: () => import('./audit/audit-log.component').then(m => m.AuditLogComponent)
  },
  {
    path: 'settings',
    loadComponent: () => import('./settings/settings.component').then(m => m.SettingsComponent)
  },
  {
    path: 'branches',
    loadComponent: () => import('./branches/branch-admin.component').then(m => m.BranchAdminComponent)
  },
  {
    path: 'routes',
    loadComponent: () => import('./routes/route-admin.component').then(m => m.RouteAdminComponent)
  },
  {
    path: 'currencies',
    loadComponent: () => import('./currencies/currency-admin.component').then(m => m.CurrencyAdminComponent)
  },
  {
    path: 'fx-rates',
    loadComponent: () => import('./currencies/fx-rate-admin.component').then(m => m.FxRateAdminComponent)
  },
  {
    path: 'tills',
    loadComponent: () => import('./tills/till-admin.component').then(m => m.TillAdminComponent)
  },
  {
    path: 'pos-sales',
    loadComponent: () => import('./pos-sales/pos-sales.component').then(m => m.PosSalesComponent)
  }
];

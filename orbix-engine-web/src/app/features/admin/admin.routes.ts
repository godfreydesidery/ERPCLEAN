import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./admin.component').then(m => m.AdminComponent)
  },
  {
    path: 'roles',
    loadComponent: () => import('./roles/role-admin.component').then(m => m.RoleAdminComponent)
  },
  {
    path: 'branches',
    loadComponent: () => import('./branches/branch-admin.component').then(m => m.BranchAdminComponent)
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

import { Routes } from '@angular/router';

/**
 * Slice G / G.1 — debt-module routes.
 *
 *   /debt                              → DebtComponent (AR/AP tab dunning queue landing)
 *   /debt/customer/uid/:uid            → DebtCustomerComponent (AR drill-down)
 *   /debt/supplier/uid/:uid            → DebtSupplierComponent (AP drill-down)
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./debt.component').then(m => m.DebtComponent)
  },
  {
    path: 'customer/uid/:uid',
    loadComponent: () => import('./debt-customer.component').then(m => m.DebtCustomerComponent)
  },
  {
    path: 'supplier/uid/:uid',
    loadComponent: () => import('./debt-supplier.component').then(m => m.DebtSupplierComponent)
  }
];

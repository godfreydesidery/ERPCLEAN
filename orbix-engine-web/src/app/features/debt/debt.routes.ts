import { Routes } from '@angular/router';

/**
 * Slice G — debt-module routes.
 *
 *   /debt                              → DebtComponent (dunning queue landing)
 *   /debt/customer/uid/:uid            → DebtCustomerComponent (drill-down)
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./debt.component').then(m => m.DebtComponent)
  },
  {
    path: 'customer/uid/:uid',
    loadComponent: () => import('./debt-customer.component').then(m => m.DebtCustomerComponent)
  }
];

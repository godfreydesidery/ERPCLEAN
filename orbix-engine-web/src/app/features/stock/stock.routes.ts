import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'balances' },
  {
    path: 'balances',
    loadComponent: () => import('./balances.component').then(m => m.BalancesComponent)
  },
  {
    path: 'card/:itemId',
    loadComponent: () => import('./stock-card.component').then(m => m.StockCardComponent)
  },
  {
    path: 'counts',
    loadComponent: () => import('./counts.component').then(m => m.CountsComponent)
  },
  {
    path: 'transfers',
    loadComponent: () => import('./transfers.component').then(m => m.TransfersComponent)
  }
];

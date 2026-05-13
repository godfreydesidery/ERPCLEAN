import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./stock.component').then(m => m.StockComponent)
  }
];

import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./sales.component').then(m => m.SalesComponent)
  },
  {
    path: 'invoices',
    loadComponent: () => import('./invoices.component').then(m => m.InvoicesComponent)
  },
  {
    path: 'receipts',
    loadComponent: () => import('./receipts.component').then(m => m.ReceiptsComponent)
  },
  {
    path: 'returns',
    loadComponent: () => import('./returns.component').then(m => m.ReturnsComponent)
  },
  {
    path: 'packing-lists',
    loadComponent: () => import('./packing-lists.component').then(m => m.PackingListsComponent)
  }
];

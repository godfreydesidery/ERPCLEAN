import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'invoices' },
  {
    path: 'invoices',
    loadComponent: () => import('./invoices.component').then(m => m.InvoicesComponent)
  },
  {
    path: 'receipts',
    loadComponent: () => import('./receipts.component').then(m => m.ReceiptsComponent)
  }
];

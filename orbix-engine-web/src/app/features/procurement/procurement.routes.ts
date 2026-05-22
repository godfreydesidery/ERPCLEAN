import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./procurement.component').then(m => m.ProcurementComponent)
  },
  {
    path: 'lpos',
    loadComponent: () => import('./lpos.component').then(m => m.LposComponent)
  },
  {
    path: 'grns',
    loadComponent: () => import('./grns.component').then(m => m.GrnsComponent)
  },
  {
    path: 'invoices',
    loadComponent: () => import('./invoices.component').then(m => m.InvoicesComponent)
  },
  {
    path: 'payments',
    loadComponent: () => import('./payments.component').then(m => m.PaymentsComponent)
  }
];

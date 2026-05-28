import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./cash.component').then(m => m.CashComponent)
  },
  {
    path: 'books',
    loadComponent: () => import('./cash-books.component').then(m => m.CashBooksComponent)
  },
  {
    path: 'entries',
    loadComponent: () => import('./cash-entries.component').then(m => m.CashEntriesComponent)
  },
  {
    path: 'adjustments',
    loadComponent: () => import('./cash-adjustments.component').then(m => m.CashAdjustmentsComponent)
  },
  {
    path: 'bank-deposits',
    loadComponent: () => import('./bank-deposits.component').then(m => m.BankDepositsComponent)
  }
];

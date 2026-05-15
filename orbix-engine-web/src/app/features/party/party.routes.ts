import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'customers' },
  {
    path: 'customers',
    loadComponent: () => import('./customers.component').then(m => m.CustomersComponent)
  },
  {
    path: 'suppliers',
    loadComponent: () => import('./suppliers.component').then(m => m.SuppliersComponent)
  },
  {
    path: 'employees',
    loadComponent: () => import('./employees.component').then(m => m.EmployeesComponent)
  },
  {
    path: 'agents',
    loadComponent: () => import('./sales-agents.component').then(m => m.SalesAgentsComponent)
  }
];

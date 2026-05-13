import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./procurement.component').then(m => m.ProcurementComponent)
  }
];

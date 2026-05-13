import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./production.component').then(m => m.ProductionComponent)
  }
];

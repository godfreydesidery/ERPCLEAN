import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./catalog.component').then(m => m.CatalogComponent)
  }
];

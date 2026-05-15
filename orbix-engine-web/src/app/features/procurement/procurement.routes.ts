import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'lpos' },
  {
    path: 'lpos',
    loadComponent: () => import('./lpos.component').then(m => m.LposComponent)
  },
  {
    path: 'grns',
    loadComponent: () => import('./grns.component').then(m => m.GrnsComponent)
  }
];

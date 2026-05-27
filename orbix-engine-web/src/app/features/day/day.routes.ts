import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./day.component').then(m => m.DayComponent)
  },
  {
    path: 'overrides',
    loadComponent: () => import('./day-overrides.component').then(m => m.DayOverridesComponent)
  }
];

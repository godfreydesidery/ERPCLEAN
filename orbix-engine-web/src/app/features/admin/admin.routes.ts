import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./admin.component').then(m => m.AdminComponent)
  },
  {
    path: 'roles',
    loadComponent: () => import('./roles/role-admin.component').then(m => m.RoleAdminComponent)
  },
  {
    path: 'branches',
    loadComponent: () => import('./branches/branch-admin.component').then(m => m.BranchAdminComponent)
  }
];

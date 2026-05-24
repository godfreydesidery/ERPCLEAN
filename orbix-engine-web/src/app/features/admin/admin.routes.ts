import { Routes } from '@angular/router';
import { permissionGuard } from '../../core/auth/permission.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./admin.component').then(m => m.AdminComponent)
  },
  {
    path: 'users',
    canActivate: [permissionGuard('IAM.MANAGE_USERS')],
    loadComponent: () => import('./users/user-admin.component').then(m => m.UserAdminComponent)
  },
  {
    path: 'users/:uid',
    canActivate: [permissionGuard('IAM.MANAGE_USERS')],
    loadComponent: () => import('./users/user-detail.component').then(m => m.UserDetailComponent)
  },
  {
    path: 'roles',
    canActivate: [permissionGuard('IAM.MANAGE_ROLES')],
    loadComponent: () => import('./roles/role-admin.component').then(m => m.RoleAdminComponent)
  },
  {
    path: 'audit',
    canActivate: [permissionGuard('IAM.VIEW_AUDIT')],
    loadComponent: () => import('./audit/audit-log.component').then(m => m.AuditLogComponent)
  },
  {
    path: 'settings',
    canActivate: [permissionGuard('ADMIN.MANAGE_SETTINGS')],
    loadComponent: () => import('./settings/settings.component').then(m => m.SettingsComponent)
  },
  {
    path: 'company',
    canActivate: [permissionGuard('ADMIN.MANAGE_SETTINGS')],
    loadComponent: () => import('./company/company.component').then(m => m.CompanyProfileComponent)
  },
  {
    path: 'branches',
    canActivate: [permissionGuard('ADMIN.MANAGE_BRANCHES')],
    loadComponent: () => import('./branches/branch-admin.component').then(m => m.BranchAdminComponent)
  },
  {
    path: 'routes',
    canActivate: [permissionGuard('ADMIN.MANAGE_ROUTES')],
    loadComponent: () => import('./routes/route-admin.component').then(m => m.RouteAdminComponent)
  },
  {
    path: 'currencies',
    canActivate: [permissionGuard('ADMIN.MANAGE_CURRENCIES')],
    loadComponent: () => import('./currencies/currency-admin.component').then(m => m.CurrencyAdminComponent)
  },
  {
    path: 'fx-rates',
    canActivate: [permissionGuard('ADMIN.MANAGE_FX')],
    loadComponent: () => import('./currencies/fx-rate-admin.component').then(m => m.FxRateAdminComponent)
  },
  {
    path: 'tills',
    canActivate: [permissionGuard('ADMIN.MANAGE_BRANCHES')],
    loadComponent: () => import('./tills/till-admin.component').then(m => m.TillAdminComponent)
  },
  {
    path: 'pos-sales',
    canActivate: [permissionGuard('POS.VIEW')],
    loadComponent: () => import('./pos-sales/pos-sales.component').then(m => m.PosSalesComponent)
  }
];

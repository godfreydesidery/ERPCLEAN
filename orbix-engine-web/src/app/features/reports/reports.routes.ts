import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./reports.component').then(m => m.ReportsComponent)
  },
  {
    // Slice F — KPI 1.A drill-through destination. Accepts
    // ?branchId=...&businessDate=YYYY-MM-DD from the dashboard tile.
    path: 'sales-daily',
    loadComponent: () => import('./sales-daily.component').then(m => m.SalesDailyComponent)
  }
];

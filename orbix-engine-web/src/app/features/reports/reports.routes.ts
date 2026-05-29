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
  },
  {
    // Slice I — US-RPT-002. Accepts ?branchId=...&businessDate=YYYY-MM-DD.
    path: 'sales-summary',
    loadComponent: () => import('./sales-summary.component').then(m => m.SalesSummaryComponent)
  },
  {
    // Slice I — US-RPT-003. Accepts ?branchId=...&from=YYYY-MM-DD&to=YYYY-MM-DD.
    path: 'z-history',
    loadComponent: () => import('./z-history.component').then(m => m.ZHistoryComponent)
  },
  {
    // Slice J — US-RPT-004. Accepts ?itemId=&branchId=.
    path: 'stock-card',
    loadComponent: () => import('./stock-card.component').then(m => m.StockCardComponent)
  },
  {
    // Slice J — US-RPT-006. Accepts ?branchId=.
    path: 'negative-stock',
    loadComponent: () => import('./negative-stock.component').then(m => m.NegativeStockComponent)
  },
  {
    // Slice J — US-RPT-005. Accepts ?branchId=.
    path: 'stock-movers',
    loadComponent: () => import('./stock-movers.component').then(m => m.StockMoversComponent)
  },
  {
    // Slice K — US-DEBT-005 / US-RPT-007. Accepts ?customerId=&from=&to=.
    path: 'customer-statement',
    loadComponent: () => import('./customer-statement.component').then(m => m.CustomerStatementComponent)
  },
  {
    // Slice K — US-DEBT-006 / US-RPT-007. Accepts ?supplierId=&from=&to=.
    path: 'supplier-statement',
    loadComponent: () => import('./supplier-statement.component').then(m => m.SupplierStatementComponent)
  },
  {
    // Slice K — US-RPT-014. Accepts ?branchId=&type=&asOf=.
    // Gated by ORDER.READ on the backend; FE renders a permission-required panel for cashier.
    path: 'layby-ageing',
    loadComponent: () => import('./layby-ageing.component').then(m => m.LaybyAgeingComponent)
  }
];

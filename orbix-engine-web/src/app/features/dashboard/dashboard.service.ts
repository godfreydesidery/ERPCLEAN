import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, unwrap } from '../../core/api/api-response';
import { SalesService } from '../sales/sales.service';
import { ArSummary } from '../sales/sales.models';
import { StockService } from '../stock/stock.service';

/**
 * Dashboard metrics aggregator (web-side).
 *
 * The dashboard spans several modules (sales, stock, procurement, debt). Per the
 * module-boundary rules the backend modules don't reach across each other, so the
 * composition happens here in the UI.
 *
 * Each metric is either:
 *   - LIVE  — backed by a real endpoint that exists today, or
 *   - STUB  — no aggregate endpoint exists yet; returns a depicted sample value.
 *             When the backend resource lands, swap the `of(...)` for the real
 *             HTTP call below it (one line) — nothing else needs to change.
 *
 * Keep {@link DASHBOARD_LIVE} in sync so the UI can flag sample tiles honestly.
 */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly stock = inject(StockService);
  private readonly sales = inject(SalesService);
  private readonly base = environment.apiUrl;

  // ---- LIVE metrics (real endpoints) ----------------------------------------

  /** Today's sales grand total (POS + credit invoices) for a branch + business date. */
  todaysSales(branchId: string, businessDate: string): Observable<number> {
    const params = new HttpParams().set('branchId', branchId).set('businessDate', businessDate);
    return unwrap(
      this.http.get<ApiResponse<DailySummaryResponse>>(`${this.base}/reports/sales-summary`, { params })
    ).pipe(map(s => s.sales?.grandTotal ?? 0));
  }

  /** Count of items at or below their reorder minimum in a branch. */
  stockAlertCount(branchId: string): Observable<number> {
    return this.stock.listBalances(branchId).pipe(
      map(rows => rows.filter(b => b.reorderMin !== null && b.qtyOnHand <= b.reorderMin).length)
    );
  }

  /**
   * Count of item-branch balances currently in negative on-hand. Branch-scoped
   * when {@code branchId} is set; company-wide otherwise. Backed by Slice E1's
   * {@code GET /api/v1/reports/stock-negative} (permission STOCK.COUNT) — the
   * dashboard tile surfaces 403 as "Permission required" via the component.
   */
  negativeStockCount(branchId: string | null): Observable<number> {
    return this.stock.getNegativeStockReport(branchId).pipe(map(rows => rows.length));
  }

  /**
   * AR rollup for the dashboard tiles. {@code branchId} null returns
   * a company-wide total. Backed by the Slice C
   * {@code GET /sales/reports/ar-summary} endpoint.
   */
  arSummary(branchId: string | null): Observable<ArSummary> {
    return this.sales.getArSummary(branchId);
  }

  // ---- STUB metrics (no aggregate endpoint yet) -----------------------------
  // Sample values so the dashboard depicts a populated environment. Replace each
  // `of(...)` with the real call when the endpoint exists.

  /** Connect: GET /reports/approvals-pending?branchId -> count of LPO in PENDING_APPROVAL */
  lposPendingApproval(): Observable<number> {
    return of(SAMPLE.lposPendingApproval);
  }
}

/** Which metrics are backed by a real endpoint today (the rest are samples). */
export const DASHBOARD_LIVE = {
  todaysSales: true,
  stockAlertCount: true,
  negativeStockCount: true,
  openInvoiceCount: true,
  arOutstanding: true,
  overdueInvoiceCount: true,
  lposPendingApproval: false,
} as const;

/** Depicted values for metrics whose backend resource doesn't exist yet. */
const SAMPLE = {
  lposPendingApproval: 2,
} as const;

/** Minimal shape of the backend DailySummaryDto we read here. */
interface DailySummaryResponse {
  sales: { grandTotal: number } | null;
}

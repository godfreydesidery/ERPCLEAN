import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
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
 * Slice F — preferred entry point is {@link rollup} which collapses the four
 * parallel per-fragment calls (ar-summary + stock-negative + lpo-pending-count +
 * sales-summary + balances-derived stock-alert-count) into a single
 * {@code GET /api/v1/reports/dashboard-rollup} round-trip. Per-fragment
 * authorisation: fragments the caller lacks the perm for serialise as
 * {@code null} (no top-level 403). The component renders {@code null} as
 * "Permission required" — same UX as the previous per-tile 403 handling.
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

  /**
   * Slice F — consolidated dashboard rollup. Returns the union of all KPI tile
   * values + alert counts in one trip. Per-fragment authorisation: fragments
   * the caller lacks the perm for serialise as {@code null}. The dashboard
   * renders {@code null} as "Permission required" via the existing
   * {@code arPermissionDenied} / {@code negativeStockPermissionDenied}
   * signals (driven from the single response).
   *
   * <p>{@code branchId} null = company-wide; {@code businessDate} omitted =
   * today. The backend stringifies the {@code branchId} Long so it arrives as
   * a string on the wire (matches the project's JSON:API discipline).
   */
  rollup(branchId: string | null, businessDate: string): Observable<DashboardRollupResponse> {
    let params = new HttpParams().set('businessDate', businessDate);
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(
      this.http.get<ApiResponse<DashboardRollupResponse>>(
        `${this.base}/reports/dashboard-rollup`, { params }
      )
    );
  }

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

  /**
   * Count of LPOs in PENDING_APPROVAL. Branch-scoped when {@code branchId} is
   * set; company-wide otherwise. Backed by
   * {@code GET /api/v1/lpos/pending-approval/count} (permission MANAGE_LPO /
   * APPROVE_LPO / MANAGE_LPO.READ) — 403 surfaces as a hidden tile via the
   * component (the alert only renders when count > 0).
   */
  lposPendingApproval(branchId: string | null): Observable<number> {
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    return unwrap(
      this.http.get<ApiResponse<{ count: number }>>(`${this.base}/lpos/pending-approval/count`, { params })
    ).pipe(map(r => r.count ?? 0));
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
  lposPendingApproval: true,
  dashboardRollup: true,
} as const;

/** Minimal shape of the backend DailySummaryDto we read here. */
interface DailySummaryResponse {
  sales: { grandTotal: number } | null;
}

/**
 * Mirrors the backend {@code DashboardRollupDto}. Long ids stringify on the
 * wire per project convention ({@code IdLongAsStringSerializerModifier}), so
 * {@code branchId} arrives as {@code string | null}. BigDecimals remain numeric.
 * Fragments serialise as {@code null} when the caller lacks the per-fragment
 * permission.
 */
export interface DashboardRollupResponse {
  branchId: string | null;
  businessDate: string;
  currencyCode: string;
  kpi: DashboardRollupKpi | null;
  alerts: DashboardRollupAlerts | null;
}

export interface DashboardRollupKpi {
  todaysSales: number | null;
  stockAlerts: number | null;
  negativeStockCount: number | null;
  openInvoices: number | null;
  arOutstanding: number | null;
}

export interface DashboardRollupAlerts {
  stockAlertCount: number | null;
  negativeStockCount: number | null;
  overdueInvoiceCount: number | null;
  lposPendingApproval: number | null;
}

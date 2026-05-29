import { Component } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';

interface ReportTile {
  label: string;
  description: string;
  link: string;
  external?: boolean;
  icon: string;
  tint: 'blue' | 'green' | 'amber' | 'rose' | 'violet' | 'orange';
  status: 'live' | 'soon';
}

@Component({
  selector: 'orbix-reports',
  standalone: true,
  imports: [NgTemplateOutlet, RouterLink],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Finance</p>
      <h1 class="h3 fw-bold mb-1 text-dark">Reports</h1>
      <p class="text-secondary mb-0 small">Statements, ageing, sales and stock summaries.</p>
    </header>

    <div class="row g-3 g-md-4">
      @for (tile of tiles; track tile.label) {
        <div class="col-12 col-sm-6 col-lg-4">
          @if (tile.status === 'live') {
            <a [routerLink]="tile.link" class="report-tile h-100 text-decoration-none">
              <ng-container *ngTemplateOutlet="body; context: { $implicit: tile }"></ng-container>
            </a>
          } @else {
            <div class="report-tile is-soon h-100">
              <ng-container *ngTemplateOutlet="body; context: { $implicit: tile }"></ng-container>
            </div>
          }
        </div>
      }
    </div>

    <ng-template #body let-tile>
      <span class="report-tile__icon report-tile__icon--{{ tile.tint }}">
        <i class="bi {{ tile.icon }}"></i>
      </span>
      <div class="flex-grow-1 min-w-0">
        <div class="d-flex align-items-center gap-2 mb-1">
          <h2 class="h6 fw-bold mb-0 text-dark text-truncate">{{ tile.label }}</h2>
          @if (tile.status === 'soon') {
            <span class="badge text-bg-light text-secondary small">SOON</span>
          }
        </div>
        <p class="small text-secondary mb-0">{{ tile.description }}</p>
      </div>
      @if (tile.status === 'live') {
        <i class="bi bi-arrow-right text-secondary report-tile__chev"></i>
      }
    </ng-template>
  `,
  styles: [`
    :host { display: block; }
    .min-w-0 { min-width: 0; }

    .report-tile {
      display: flex; align-items: center; gap: 1rem;
      padding: 1.25rem; background: #fff; border: 1px solid #e5e7eb; border-radius: 14px;
      color: inherit; transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
    }
    a.report-tile:hover {
      border-color: #1d4ed8; transform: translateY(-2px);
      box-shadow: 0 0.5rem 1.25rem rgba(13, 42, 91, 0.08);
    }
    a.report-tile:hover .report-tile__chev { color: #1d4ed8 !important; transform: translateX(2px); }
    .report-tile.is-soon { opacity: 0.7; cursor: default; }

    .report-tile__icon {
      width: 48px; height: 48px; border-radius: 12px;
      display: inline-flex; align-items: center; justify-content: center;
      font-size: 1.35rem; flex-shrink: 0;
    }
    .report-tile__icon--blue   { background: #e0ecff; color: #1d4ed8; }
    .report-tile__icon--green  { background: #d1fae5; color: #047857; }
    .report-tile__icon--amber  { background: #fef3c7; color: #b45309; }
    .report-tile__icon--rose   { background: #ffe4e6; color: #be123c; }
    .report-tile__icon--violet { background: #ede9fe; color: #6d28d9; }
    .report-tile__icon--orange { background: #ffedd5; color: #c2410c; }
    .report-tile__chev { transition: transform 0.15s ease, color 0.15s ease; }

    @media (prefers-reduced-motion: reduce) {
      .report-tile, .report-tile__chev { transition: none; }
      a.report-tile:hover { transform: none; }
    }
  `]
})
export class ReportsComponent {
  protected readonly tiles: ReportTile[] = [
    { label: 'Daily sales',         description: 'Per-document list blending sales invoices and POS sales for a single business day.', link: '/reports/sales-daily',     icon: 'bi-receipt',              tint: 'blue',   status: 'live' },
    { label: 'Daily summary',       description: 'Sales, purchases and cash one-pager rollup — the manager morning report.',           link: '/reports/sales-summary',   icon: 'bi-bar-chart-line',       tint: 'green',  status: 'live' },
    { label: 'Z-history',           description: 'Closed till sessions with Z-report totals — business date, cashier, variance.',      link: '/reports/z-history',       icon: 'bi-printer',              tint: 'violet', status: 'live' },
    { label: 'Customer statement',  description: 'Per-customer AR statement with opening + period + closing balance.',                                    link: '/reports/customer-statement', icon: 'bi-file-earmark-person', tint: 'amber',  status: 'live' },
    { label: 'Supplier statement',  description: 'Per-supplier AP statement with the same ledger view.',                                                         link: '/reports/supplier-statement', icon: 'bi-truck',               tint: 'rose',   status: 'live' },
    { label: 'AR ageing',           description: 'Customer dunning queue with 5-bucket ageing breakdown. View in Debt module.',                                   link: '/debt',                       icon: 'bi-clock-history',       tint: 'amber',  status: 'live' },
    { label: 'AP ageing',           description: 'Supplier obligations queue with ageing buckets. View in Debt module.',                                          link: '/debt',                       icon: 'bi-hourglass-bottom',    tint: 'rose',   status: 'live' },
    { label: 'Layby ageing',        description: 'Outstanding layby / pre-order balances by age bucket (ORDER.READ required).',                                   link: '/reports/layby-ageing',       icon: 'bi-bag-check',           tint: 'orange', status: 'live' },
    { label: 'Stock card',          description: 'Chronological stock movements for an item at a branch — GRNs, sales, adjustments.', link: '/reports/stock-card',         icon: 'bi-clipboard2-pulse',    tint: 'blue',   status: 'live' },
    { label: 'Negative stock',      description: 'Items with on-hand quantity below zero — drill through to the stock card.',          link: '/reports/negative-stock',     icon: 'bi-exclamation-octagon', tint: 'rose',   status: 'live' },
    { label: 'Stock movers',        description: 'Top fast and slow moving items over a chosen date range and move-type set.',         link: '/reports/stock-movers',       icon: 'bi-lightning-charge',    tint: 'amber',  status: 'live' },
  ];
}

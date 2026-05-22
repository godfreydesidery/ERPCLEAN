import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface SalesTile {
  label: string;
  description: string;
  link: string;
  icon: string;
  tint: 'blue' | 'green' | 'amber' | 'rose';
}

@Component({
  selector: 'orbix-sales',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Commerce</p>
      <h1 class="h3 fw-bold mb-1 text-dark">Sales</h1>
      <p class="text-secondary mb-0 small">Customer invoicing, receipts, returns and packing lists.</p>
    </header>

    <div class="row g-3 g-md-4">
      @for (tile of tiles; track tile.label) {
        <div class="col-12 col-sm-6 col-lg-6">
          <a [routerLink]="tile.link" class="sales-tile h-100 text-decoration-none">
            <span class="sales-tile__icon sales-tile__icon--{{ tile.tint }}">
              <i class="bi {{ tile.icon }}"></i>
            </span>
            <div class="flex-grow-1">
              <h2 class="h6 fw-bold mb-1 text-dark">{{ tile.label }}</h2>
              <p class="small text-secondary mb-0">{{ tile.description }}</p>
            </div>
            <i class="bi bi-arrow-right text-secondary sales-tile__chev"></i>
          </a>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .sales-tile {
      display: flex; align-items: center; gap: 1rem;
      padding: 1.25rem; background: #fff; border: 1px solid #e5e7eb; border-radius: 14px;
      color: inherit; transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
    }
    .sales-tile:hover {
      border-color: #1d4ed8; transform: translateY(-2px);
      box-shadow: 0 0.5rem 1.25rem rgba(13, 42, 91, 0.08);
    }
    .sales-tile:hover .sales-tile__chev { color: #1d4ed8 !important; transform: translateX(2px); }
    .sales-tile__icon {
      width: 48px; height: 48px; border-radius: 12px;
      display: inline-flex; align-items: center; justify-content: center;
      font-size: 1.35rem; flex-shrink: 0;
    }
    .sales-tile__icon--blue   { background: #e0ecff; color: #1d4ed8; }
    .sales-tile__icon--green  { background: #d1fae5; color: #047857; }
    .sales-tile__icon--amber  { background: #fef3c7; color: #b45309; }
    .sales-tile__icon--rose   { background: #ffe4e6; color: #be123c; }
    .sales-tile__chev { transition: transform 0.15s ease, color 0.15s ease; }
    @media (prefers-reduced-motion: reduce) {
      .sales-tile, .sales-tile__chev { transition: none; }
      .sales-tile:hover { transform: none; }
    }
  `]
})
export class SalesComponent {
  protected readonly tiles: SalesTile[] = [
    { label: 'Invoices',      description: 'Draft, post, void and view sales invoices.',      link: 'invoices',      icon: 'bi-receipt',           tint: 'blue'  },
    { label: 'Receipts',      description: 'Record customer payments against invoices.',     link: 'receipts',      icon: 'bi-cash-coin',         tint: 'green' },
    { label: 'Returns',       description: 'Process credit notes and customer returns.',     link: 'returns',       icon: 'bi-arrow-counterclockwise', tint: 'rose'  },
    { label: 'Packing lists', description: 'Pick, pack and dispatch customer orders.',       link: 'packing-lists', icon: 'bi-box-seam',          tint: 'amber' },
  ];
}

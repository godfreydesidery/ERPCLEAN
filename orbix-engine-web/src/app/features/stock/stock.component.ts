import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface StockTile {
  label: string;
  description: string;
  link: string;
  icon: string;
  tint: 'blue' | 'green' | 'amber' | 'rose' | 'violet' | 'orange';
}

@Component({
  selector: 'orbix-stock',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Operations</p>
      <h1 class="h3 fw-bold mb-1 text-dark">Stock</h1>
      <p class="text-secondary mb-0 small">Balances, batches, transfers, counts, adjustments and internal draws.</p>
    </header>

    <div class="row g-3 g-md-4">
      @for (tile of tiles; track tile.label) {
        <div class="col-12 col-sm-6 col-lg-4">
          <a [routerLink]="tile.link" class="stock-tile h-100 text-decoration-none">
            <span class="stock-tile__icon stock-tile__icon--{{ tile.tint }}">
              <i class="bi {{ tile.icon }}"></i>
            </span>
            <div class="flex-grow-1">
              <h2 class="h6 fw-bold mb-1 text-dark">{{ tile.label }}</h2>
              <p class="small text-secondary mb-0">{{ tile.description }}</p>
            </div>
            <i class="bi bi-arrow-right text-secondary stock-tile__chev"></i>
          </a>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .stock-tile {
      display: flex; align-items: center; gap: 1rem;
      padding: 1.25rem; background: #fff; border: 1px solid #e5e7eb; border-radius: 14px;
      color: inherit; transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
    }
    .stock-tile:hover {
      border-color: #1d4ed8; transform: translateY(-2px);
      box-shadow: 0 0.5rem 1.25rem rgba(13, 42, 91, 0.08);
    }
    .stock-tile:hover .stock-tile__chev { color: #1d4ed8 !important; transform: translateX(2px); }
    .stock-tile__icon {
      width: 48px; height: 48px; border-radius: 12px;
      display: inline-flex; align-items: center; justify-content: center;
      font-size: 1.35rem; flex-shrink: 0;
    }
    .stock-tile__icon--blue   { background: #e0ecff; color: #1d4ed8; }
    .stock-tile__icon--green  { background: #d1fae5; color: #047857; }
    .stock-tile__icon--amber  { background: #fef3c7; color: #b45309; }
    .stock-tile__icon--rose   { background: #ffe4e6; color: #be123c; }
    .stock-tile__icon--violet { background: #ede9fe; color: #6d28d9; }
    .stock-tile__icon--orange { background: #ffedd5; color: #c2410c; }
    .stock-tile__chev { transition: transform 0.15s ease, color 0.15s ease; }
    @media (prefers-reduced-motion: reduce) {
      .stock-tile, .stock-tile__chev { transition: none; }
      .stock-tile:hover { transform: none; }
    }
  `]
})
export class StockComponent {
  protected readonly tiles: StockTile[] = [
    { label: 'Stock balances',       description: 'On-hand, reserved and in-transit per item.',     link: 'balances',             icon: 'bi-boxes',            tint: 'blue'   },
    { label: 'Batches',              description: 'Track batches, expiry and recalls.',             link: 'batches',              icon: 'bi-collection',       tint: 'amber'  },
    { label: 'Stock transfers',      description: 'Move stock between branches.',                   link: 'transfers',            icon: 'bi-arrow-left-right', tint: 'violet' },
    { label: 'Stock counts',         description: 'Cycle and full counts with variance posting.',   link: 'counts',               icon: 'bi-clipboard-check',  tint: 'green'  },
    { label: 'Adjustments',          description: 'Shrinkage, finds and other manual moves.',       link: 'adjust',               icon: 'bi-sliders',          tint: 'rose'   },
    { label: 'Internal consumption', description: 'Draws for canteen, breakage and own use.',       link: 'internal-consumption', icon: 'bi-mug-hot',          tint: 'orange' },
  ];
}

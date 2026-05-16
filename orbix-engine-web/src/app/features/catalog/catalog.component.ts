import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface CatalogTile {
  label: string;
  description: string;
  link: string;
  icon: string;
  tint: 'blue' | 'green' | 'amber' | 'rose' | 'violet';
}

@Component({
  selector: 'orbix-catalog',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Operations</p>
      <h1 class="h3 fw-bold mb-1 text-dark">Catalog</h1>
      <p class="text-secondary mb-0 small">Items, groups, units of measure, tax classes and price books.</p>
    </header>

    <div class="row g-3 g-md-4">
      @for (tile of tiles; track tile.label) {
        <div class="col-12 col-sm-6 col-lg-4">
          <a [routerLink]="tile.link" class="catalog-tile h-100 text-decoration-none">
            <span class="catalog-tile__icon catalog-tile__icon--{{ tile.tint }}">
              <i class="bi {{ tile.icon }}"></i>
            </span>
            <div class="flex-grow-1">
              <h2 class="h6 fw-bold mb-1 text-dark">{{ tile.label }}</h2>
              <p class="small text-secondary mb-0">{{ tile.description }}</p>
            </div>
            <i class="bi bi-arrow-right text-secondary catalog-tile__chev"></i>
          </a>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }

    .catalog-tile {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 1.25rem;
      background: #fff;
      border: 1px solid #e5e7eb;
      border-radius: 14px;
      color: inherit;
      transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
    }
    .catalog-tile:hover {
      border-color: #1d4ed8;
      transform: translateY(-2px);
      box-shadow: 0 0.5rem 1.25rem rgba(13, 42, 91, 0.08);
    }
    .catalog-tile:hover .catalog-tile__chev { color: #1d4ed8 !important; transform: translateX(2px); }

    .catalog-tile__icon {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 1.35rem;
      flex-shrink: 0;
    }
    .catalog-tile__icon--blue   { background: #e0ecff; color: #1d4ed8; }
    .catalog-tile__icon--green  { background: #d1fae5; color: #047857; }
    .catalog-tile__icon--amber  { background: #fef3c7; color: #b45309; }
    .catalog-tile__icon--rose   { background: #ffe4e6; color: #be123c; }
    .catalog-tile__icon--violet { background: #ede9fe; color: #6d28d9; }

    .catalog-tile__chev { transition: transform 0.15s ease, color 0.15s ease; }

    @media (prefers-reduced-motion: reduce) {
      .catalog-tile, .catalog-tile__chev { transition: none; }
      .catalog-tile:hover { transform: none; }
    }
  `]
})
export class CatalogComponent {
  protected readonly tiles: CatalogTile[] = [
    { label: 'Items',            description: 'Create, edit and archive catalog items.', link: 'items',       icon: 'bi-tag',          tint: 'blue'   },
    { label: 'Item groups',      description: 'Maintain the item-group hierarchy.',      link: 'groups',      icon: 'bi-diagram-3',    tint: 'violet' },
    { label: 'Units of measure', description: 'Register units and their dimensions.',    link: 'uoms',        icon: 'bi-rulers',       tint: 'green'  },
    { label: 'VAT groups',       description: 'Tax classifications and rates.',          link: 'vat-groups',  icon: 'bi-percent',      tint: 'amber'  },
    { label: 'Price lists',      description: 'Price books and price-change history.',   link: 'price-lists', icon: 'bi-cash-stack',   tint: 'rose'   },
  ];
}

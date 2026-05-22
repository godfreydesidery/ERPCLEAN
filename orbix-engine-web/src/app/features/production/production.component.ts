import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface ProductionTile {
  label: string;
  description: string;
  link: string;
  icon: string;
  tint: 'blue' | 'green' | 'amber' | 'violet';
  status: 'live' | 'soon';
}

@Component({
  selector: 'orbix-production',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Operations</p>
      <h1 class="h3 fw-bold mb-1 text-dark">Production</h1>
      <p class="text-secondary mb-0 small">Recipes, work orders and yield reconciliation.</p>
    </header>

    <div class="alert alert-info d-flex align-items-start gap-2 mb-4">
      <i class="bi bi-tools mt-1"></i>
      <div class="flex-grow-1 small">
        <strong>Production module coming soon.</strong> Bill-of-materials, work orders and yield variance
        screens are planned. For now, internal-consumption draws and stock adjustments cover the
        manufacturing flow.
      </div>
    </div>

    <div class="row g-3 g-md-4">
      @for (tile of tiles; track tile.label) {
        <div class="col-12 col-sm-6 col-lg-6">
          @if (tile.status === 'live') {
            <a [routerLink]="tile.link" class="prod-tile h-100 text-decoration-none">
              <span class="prod-tile__icon prod-tile__icon--{{ tile.tint }}">
                <i class="bi {{ tile.icon }}"></i>
              </span>
              <div class="flex-grow-1 min-w-0">
                <h2 class="h6 fw-bold mb-1 text-dark">{{ tile.label }}</h2>
                <p class="small text-secondary mb-0">{{ tile.description }}</p>
              </div>
              <i class="bi bi-arrow-right text-secondary prod-tile__chev"></i>
            </a>
          } @else {
            <div class="prod-tile is-soon h-100">
              <span class="prod-tile__icon prod-tile__icon--{{ tile.tint }}">
                <i class="bi {{ tile.icon }}"></i>
              </span>
              <div class="flex-grow-1 min-w-0">
                <div class="d-flex align-items-center gap-2 mb-1">
                  <h2 class="h6 fw-bold mb-0 text-dark">{{ tile.label }}</h2>
                  <span class="badge text-bg-light text-secondary small">SOON</span>
                </div>
                <p class="small text-secondary mb-0">{{ tile.description }}</p>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .min-w-0 { min-width: 0; }

    .prod-tile {
      display: flex; align-items: center; gap: 1rem;
      padding: 1.25rem; background: #fff; border: 1px solid #e5e7eb; border-radius: 14px;
      color: inherit; transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
    }
    a.prod-tile:hover {
      border-color: #1d4ed8; transform: translateY(-2px);
      box-shadow: 0 0.5rem 1.25rem rgba(13, 42, 91, 0.08);
    }
    a.prod-tile:hover .prod-tile__chev { color: #1d4ed8 !important; transform: translateX(2px); }
    .prod-tile.is-soon { opacity: 0.7; cursor: default; }

    .prod-tile__icon {
      width: 48px; height: 48px; border-radius: 12px;
      display: inline-flex; align-items: center; justify-content: center;
      font-size: 1.35rem; flex-shrink: 0;
    }
    .prod-tile__icon--blue   { background: #e0ecff; color: #1d4ed8; }
    .prod-tile__icon--green  { background: #d1fae5; color: #047857; }
    .prod-tile__icon--amber  { background: #fef3c7; color: #b45309; }
    .prod-tile__icon--violet { background: #ede9fe; color: #6d28d9; }
    .prod-tile__chev { transition: transform 0.15s ease, color 0.15s ease; }

    @media (prefers-reduced-motion: reduce) {
      .prod-tile, .prod-tile__chev { transition: none; }
      a.prod-tile:hover { transform: none; }
    }
  `]
})
export class ProductionComponent {
  protected readonly tiles: ProductionTile[] = [
    { label: 'Recipes (BoM)',         description: 'Bill-of-materials per finished good, with yield assumptions.',  link: '/production/recipes',      icon: 'bi-journal-text',     tint: 'blue',   status: 'soon' },
    { label: 'Work orders',           description: 'Schedule, post and close production runs.',                     link: '/production/work-orders',  icon: 'bi-gear-wide-connected', tint: 'amber', status: 'soon' },
    { label: 'Yield variance',        description: 'Compare expected vs actual output, by item and shift.',         link: '/production/yield',        icon: 'bi-graph-up-arrow',    tint: 'green',  status: 'soon' },
    { label: 'Internal consumption',  description: 'Live — draw raw materials and components from stock.',          link: '/stock/internal-consumption', icon: 'bi-arrow-down-right-circle', tint: 'violet', status: 'live' },
  ];
}

import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface PartyTile {
  label: string;
  description: string;
  link: string;
  icon: string;
  tint: 'blue' | 'green' | 'amber' | 'violet';
}

@Component({
  selector: 'orbix-party',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Operations</p>
      <h1 class="h3 fw-bold mb-1 text-dark">Parties</h1>
      <p class="text-secondary mb-0 small">Customers, suppliers, employees and sales agents — everyone the business deals with.</p>
    </header>

    <div class="row g-3 g-md-4">
      @for (tile of tiles; track tile.label) {
        <div class="col-12 col-sm-6 col-lg-6">
          <a [routerLink]="tile.link" class="party-tile h-100 text-decoration-none">
            <span class="party-tile__icon party-tile__icon--{{ tile.tint }}">
              <i class="bi {{ tile.icon }}"></i>
            </span>
            <div class="flex-grow-1">
              <h2 class="h6 fw-bold mb-1 text-dark">{{ tile.label }}</h2>
              <p class="small text-secondary mb-0">{{ tile.description }}</p>
            </div>
            <i class="bi bi-arrow-right text-secondary party-tile__chev"></i>
          </a>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }

    .party-tile {
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
    .party-tile:hover {
      border-color: #1d4ed8;
      transform: translateY(-2px);
      box-shadow: 0 0.5rem 1.25rem rgba(13, 42, 91, 0.08);
    }
    .party-tile:hover .party-tile__chev { color: #1d4ed8 !important; transform: translateX(2px); }

    .party-tile__icon {
      width: 48px; height: 48px; border-radius: 12px;
      display: inline-flex; align-items: center; justify-content: center;
      font-size: 1.35rem; flex-shrink: 0;
    }
    .party-tile__icon--blue   { background: #e0ecff; color: #1d4ed8; }
    .party-tile__icon--green  { background: #d1fae5; color: #047857; }
    .party-tile__icon--amber  { background: #fef3c7; color: #b45309; }
    .party-tile__icon--violet { background: #ede9fe; color: #6d28d9; }

    .party-tile__chev { transition: transform 0.15s ease, color 0.15s ease; }

    @media (prefers-reduced-motion: reduce) {
      .party-tile, .party-tile__chev { transition: none; }
      .party-tile:hover { transform: none; }
    }
  `]
})
export class PartyComponent {
  protected readonly tiles: PartyTile[] = [
    { label: 'Customers',    description: 'Buyers, credit limits and statements.',          link: 'customers',  icon: 'bi-person-circle',        tint: 'blue'   },
    { label: 'Suppliers',    description: 'Vendors, payment terms and bank details.',       link: 'suppliers',  icon: 'bi-truck',                tint: 'green'  },
    { label: 'Employees',    description: 'Staff roster linked to system users.',           link: 'employees',  icon: 'bi-person-badge',         tint: 'amber'  },
    { label: 'Sales agents', description: 'Field agents, routes and commission rates.',     link: 'agents',     icon: 'bi-graph-up-arrow',       tint: 'violet' },
  ];
}

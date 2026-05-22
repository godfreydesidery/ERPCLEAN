import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HasPermissionDirective } from '../../core/auth/has-permission.directive';

interface AdminTile {
  label: string;
  description: string;
  link: string;
  permission: string;
  icon: string;
  tint: 'blue' | 'green' | 'amber' | 'rose' | 'violet' | 'orange';
}

@Component({
  selector: 'orbix-admin',
  standalone: true,
  imports: [RouterLink, HasPermissionDirective],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Settings</p>
      <h1 class="h3 fw-bold mb-1 text-dark">Admin</h1>
      <p class="text-secondary mb-0 small">Organisation setup, security and reference data.</p>
    </header>

    <div class="row g-3 g-md-4">
      @for (tile of tiles; track tile.label) {
        <div class="col-12 col-sm-6 col-lg-4" *orbixHasPermission="tile.permission">
          <a [routerLink]="tile.link" class="admin-tile h-100 text-decoration-none">
            <span class="admin-tile__icon admin-tile__icon--{{ tile.tint }}">
              <i class="bi {{ tile.icon }}"></i>
            </span>
            <div class="flex-grow-1">
              <h2 class="h6 fw-bold mb-1 text-dark">{{ tile.label }}</h2>
              <p class="small text-secondary mb-0">{{ tile.description }}</p>
            </div>
            <i class="bi bi-arrow-right text-secondary admin-tile__chev"></i>
          </a>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }

    .admin-tile {
      display: flex; align-items: center; gap: 1rem;
      padding: 1.25rem; background: #fff; border: 1px solid #e5e7eb; border-radius: 14px;
      color: inherit; transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
    }
    .admin-tile:hover {
      border-color: #1d4ed8; transform: translateY(-2px);
      box-shadow: 0 0.5rem 1.25rem rgba(13, 42, 91, 0.08);
    }
    .admin-tile:hover .admin-tile__chev { color: #1d4ed8 !important; transform: translateX(2px); }

    .admin-tile__icon {
      width: 48px; height: 48px; border-radius: 12px;
      display: inline-flex; align-items: center; justify-content: center;
      font-size: 1.35rem; flex-shrink: 0;
    }
    .admin-tile__icon--blue   { background: #e0ecff; color: #1d4ed8; }
    .admin-tile__icon--green  { background: #d1fae5; color: #047857; }
    .admin-tile__icon--amber  { background: #fef3c7; color: #b45309; }
    .admin-tile__icon--rose   { background: #ffe4e6; color: #be123c; }
    .admin-tile__icon--violet { background: #ede9fe; color: #6d28d9; }
    .admin-tile__icon--orange { background: #ffedd5; color: #c2410c; }
    .admin-tile__chev { transition: transform 0.15s ease, color 0.15s ease; }

    @media (prefers-reduced-motion: reduce) {
      .admin-tile, .admin-tile__chev { transition: none; }
      .admin-tile:hover { transform: none; }
    }
  `]
})
export class AdminComponent {
  protected readonly tiles: AdminTile[] = [
    { label: 'Users',               description: 'Create staff accounts, reset passwords, disable.',    link: 'users',      permission: 'IAM.MANAGE_USERS',     icon: 'bi-people',       tint: 'blue'   },
    { label: 'Roles & permissions', description: 'Create roles, assign permissions, grant to users.',  link: 'roles',      permission: 'IAM.MANAGE_ROLES',     icon: 'bi-shield-lock',  tint: 'rose'   },
    { label: 'Branches & sections', description: 'Manage branches and their internal sections.',       link: 'branches',   permission: 'ADMIN.MANAGE_BRANCHES', icon: 'bi-building',     tint: 'green'  },
    { label: 'Tills',               description: 'POS terminals registered against each branch.',      link: 'tills',      permission: 'ADMIN.MANAGE_BRANCHES', icon: 'bi-cash-stack',   tint: 'amber'  },
    { label: 'Currencies',          description: 'Register and enable currencies.',                     link: 'currencies', permission: 'ADMIN.MANAGE_CURRENCIES', icon: 'bi-currency-exchange', tint: 'violet' },
    { label: 'FX rates',            description: 'Quote exchange rates with full history.',             link: 'fx-rates',   permission: 'ADMIN.MANAGE_FX',      icon: 'bi-graph-up',     tint: 'orange' },
    { label: 'POS sales',           description: 'Inspect till transactions and reconciliations.',      link: 'pos-sales',  permission: 'POS.VIEW',             icon: 'bi-receipt',      tint: 'rose'   },
  ];
}

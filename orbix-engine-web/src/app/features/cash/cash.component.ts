import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HasPermissionDirective } from '../../core/auth/has-permission.directive';

interface CashTile {
  label: string;
  description: string;
  link: string;
  icon: string;
  tint: 'blue' | 'green' | 'amber' | 'violet';
  permission: string;
}

/**
 * Cash module landing page — tiles for the four read/write surfaces. Each
 * tile is gated by the relevant {@code CASH.*} permission so users see only
 * what they can act on.
 */
@Component({
  selector: 'orbix-cash',
  standalone: true,
  imports: [RouterLink, HasPermissionDirective],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Finance</p>
      <h1 class="h3 fw-bold mb-1 text-dark">Cash</h1>
      <p class="text-secondary mb-0 small">
        Ledger, balances, supervisor adjustments and EOD bank deposits.
      </p>
    </header>

    <div class="row g-3 g-md-4">
      @for (tile of tiles; track tile.label) {
        <div class="col-12 col-sm-6 col-lg-6" *orbixHasPermission="tile.permission">
          <a [routerLink]="tile.link" class="cash-tile h-100 text-decoration-none">
            <span class="cash-tile__icon cash-tile__icon--{{ tile.tint }}">
              <i class="bi {{ tile.icon }}"></i>
            </span>
            <div class="flex-grow-1">
              <h2 class="h6 fw-bold mb-1 text-dark">{{ tile.label }}</h2>
              <p class="small text-secondary mb-0">{{ tile.description }}</p>
            </div>
            <i class="bi bi-arrow-right text-secondary cash-tile__chev"></i>
          </a>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }

    .cash-tile {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 1.25rem;
      background: #fff;
      border: 1px solid #e6e9ee;
      border-radius: 12px;
      transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
    }
    .cash-tile:hover {
      border-color: #1a4fb5;
      box-shadow: 0 4px 12px rgba(26, 79, 181, 0.08);
      transform: translateY(-1px);
    }
    .cash-tile__icon {
      width: 56px; height: 56px; border-radius: 14px;
      display: inline-flex; align-items: center; justify-content: center;
      font-size: 1.5rem; flex-shrink: 0;
    }
    .cash-tile__icon--blue   { background: #e0ecff; color: #1d4ed8; }
    .cash-tile__icon--green  { background: #d1fae5; color: #047857; }
    .cash-tile__icon--amber  { background: #fef3c7; color: #92400e; }
    .cash-tile__icon--violet { background: #ede9fe; color: #6d28d9; }
    .cash-tile__chev {
      font-size: 1.1rem;
      transition: transform 0.15s ease, color 0.15s ease;
    }
    .cash-tile:hover .cash-tile__chev {
      transform: translateX(4px);
      color: #1a4fb5;
    }
  `]
})
export class CashComponent {
  protected readonly tiles: readonly CashTile[] = [
    {
      label: 'Cash book',
      description: 'Per-account closing balances for the trading day.',
      link: 'books',
      icon: 'bi-journal-bookmark',
      tint: 'blue',
      permission: 'CASH.BOOK.READ'
    },
    {
      label: 'Cash ledger',
      description: 'Every cash movement — append-only audit trail.',
      link: 'entries',
      icon: 'bi-list-ul',
      tint: 'violet',
      permission: 'CASH.ENTRY.READ'
    },
    {
      label: 'Adjustments',
      description: 'Supervisor cash corrections and reversals.',
      link: 'adjustments',
      icon: 'bi-pencil-square',
      tint: 'amber',
      permission: 'CASH.ADJUSTMENT.POST'
    },
    {
      label: 'Bank deposits',
      description: 'End-of-day banking and reversals.',
      link: 'bank-deposits',
      icon: 'bi-bank',
      tint: 'green',
      permission: 'CASH.BANK_DEPOSIT.POST'
    }
  ];
}

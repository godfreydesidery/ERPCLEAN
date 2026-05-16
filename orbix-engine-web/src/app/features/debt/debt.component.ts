import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface ShortcutTile {
  label: string;
  description: string;
  link: string;
  icon: string;
  tint: 'blue' | 'green' | 'amber' | 'rose';
}

@Component({
  selector: 'orbix-debt',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Finance</p>
      <h1 class="h3 fw-bold mb-1 text-dark">Debt</h1>
      <p class="text-secondary mb-0 small">Customer receivables and supplier payables — track what's owed and what's owing.</p>
    </header>

    <!-- Placeholder + planned scope -->
    <div class="alert alert-info d-flex align-items-start gap-2 mb-4">
      <i class="bi bi-tools mt-1"></i>
      <div class="flex-grow-1 small">
        <strong>Debt module coming soon.</strong> Dedicated AR / AP screens (ageing buckets, statements,
        dunning) are planned. For now use sales receipts to chase customers and procurement payments
        to settle suppliers — and pull AR / AP statements from Reports.
      </div>
    </div>

    <div class="row g-3 g-md-4">
      @for (tile of tiles; track tile.label) {
        <div class="col-12 col-sm-6 col-lg-6">
          <a [routerLink]="tile.link" class="debt-tile h-100 text-decoration-none">
            <span class="debt-tile__icon debt-tile__icon--{{ tile.tint }}">
              <i class="bi {{ tile.icon }}"></i>
            </span>
            <div class="flex-grow-1">
              <h2 class="h6 fw-bold mb-1 text-dark">{{ tile.label }}</h2>
              <p class="small text-secondary mb-0">{{ tile.description }}</p>
            </div>
            <i class="bi bi-arrow-right text-secondary debt-tile__chev"></i>
          </a>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .debt-tile {
      display: flex; align-items: center; gap: 1rem;
      padding: 1.25rem; background: #fff; border: 1px solid #e5e7eb; border-radius: 14px;
      color: inherit; transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
    }
    .debt-tile:hover {
      border-color: #1d4ed8; transform: translateY(-2px);
      box-shadow: 0 0.5rem 1.25rem rgba(13, 42, 91, 0.08);
    }
    .debt-tile:hover .debt-tile__chev { color: #1d4ed8 !important; transform: translateX(2px); }
    .debt-tile__icon {
      width: 48px; height: 48px; border-radius: 12px;
      display: inline-flex; align-items: center; justify-content: center;
      font-size: 1.35rem; flex-shrink: 0;
    }
    .debt-tile__icon--blue  { background: #e0ecff; color: #1d4ed8; }
    .debt-tile__icon--green { background: #d1fae5; color: #047857; }
    .debt-tile__icon--amber { background: #fef3c7; color: #b45309; }
    .debt-tile__icon--rose  { background: #ffe4e6; color: #be123c; }
    .debt-tile__chev { transition: transform 0.15s ease, color 0.15s ease; }
    @media (prefers-reduced-motion: reduce) {
      .debt-tile, .debt-tile__chev { transition: none; }
      .debt-tile:hover { transform: none; }
    }
  `]
})
export class DebtComponent {
  protected readonly tiles: ShortcutTile[] = [
    { label: 'Customer statements',  description: 'Per-party AR statement with opening + period + closing balance.', link: '/reports/customer-statement', icon: 'bi-file-earmark-person', tint: 'blue'  },
    { label: 'Supplier statements',  description: 'Per-party AP statement with the same ledger view.',               link: '/reports/supplier-statement', icon: 'bi-truck',               tint: 'green' },
    { label: 'Sales receipts',       description: 'Record customer payments and allocate to invoices.',              link: '/sales/receipts',             icon: 'bi-cash-coin',           tint: 'amber' },
    { label: 'Supplier payments',    description: 'Pay suppliers and allocate to outstanding invoices.',             link: '/procurement/payments',       icon: 'bi-bank',                tint: 'rose'  },
  ];
}

import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface ProcTile {
  label: string;
  description: string;
  link: string;
  icon: string;
  tint: 'blue' | 'green' | 'amber' | 'rose';
}

@Component({
  selector: 'orbix-procurement',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Commerce</p>
      <h1 class="h3 fw-bold mb-1 text-dark">Procurement</h1>
      <p class="text-secondary mb-0 small">Local purchase orders, goods receipts, supplier invoices and payments.</p>
    </header>

    <div class="row g-3 g-md-4">
      @for (tile of tiles; track tile.label) {
        <div class="col-12 col-sm-6 col-lg-6">
          <a [routerLink]="tile.link" class="proc-tile h-100 text-decoration-none">
            <span class="proc-tile__icon proc-tile__icon--{{ tile.tint }}">
              <i class="bi {{ tile.icon }}"></i>
            </span>
            <div class="flex-grow-1">
              <h2 class="h6 fw-bold mb-1 text-dark">{{ tile.label }}</h2>
              <p class="small text-secondary mb-0">{{ tile.description }}</p>
            </div>
            <i class="bi bi-arrow-right text-secondary proc-tile__chev"></i>
          </a>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .proc-tile {
      display: flex; align-items: center; gap: 1rem;
      padding: 1.25rem; background: #fff; border: 1px solid #e5e7eb; border-radius: 14px;
      color: inherit; transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
    }
    .proc-tile:hover {
      border-color: #1d4ed8; transform: translateY(-2px);
      box-shadow: 0 0.5rem 1.25rem rgba(13, 42, 91, 0.08);
    }
    .proc-tile:hover .proc-tile__chev { color: #1d4ed8 !important; transform: translateX(2px); }
    .proc-tile__icon {
      width: 48px; height: 48px; border-radius: 12px;
      display: inline-flex; align-items: center; justify-content: center;
      font-size: 1.35rem; flex-shrink: 0;
    }
    .proc-tile__icon--blue   { background: #e0ecff; color: #1d4ed8; }
    .proc-tile__icon--green  { background: #d1fae5; color: #047857; }
    .proc-tile__icon--amber  { background: #fef3c7; color: #b45309; }
    .proc-tile__icon--rose   { background: #ffe4e6; color: #be123c; }
    .proc-tile__chev { transition: transform 0.15s ease, color 0.15s ease; }
    @media (prefers-reduced-motion: reduce) {
      .proc-tile, .proc-tile__chev { transition: none; }
      .proc-tile:hover { transform: none; }
    }
  `]
})
export class ProcurementComponent {
  protected readonly tiles: ProcTile[] = [
    { label: 'Purchase orders (LPOs)', description: 'Draft, approve and track supplier orders.', link: 'lpos',     icon: 'bi-file-earmark-text', tint: 'blue'  },
    { label: 'Goods received notes',   description: 'Receive stock against open LPOs.',          link: 'grns',     icon: 'bi-box-arrow-in-down', tint: 'amber' },
    { label: 'Supplier invoices',      description: 'Match supplier bills to their GRNs.',       link: 'invoices', icon: 'bi-receipt-cutoff',    tint: 'green' },
    { label: 'Supplier payments',      description: 'Pay suppliers and allocate to invoices.',   link: 'payments', icon: 'bi-bank',              tint: 'rose'  },
  ];
}

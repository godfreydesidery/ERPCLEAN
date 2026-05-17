import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { StockService } from './stock.service';
import { StockMove } from './stock.models';

@Component({
  selector: 'orbix-stock-card',
  standalone: true,
  imports: [CommonModule, RouterLink, DatePipe, DecimalPipe],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink="/stock" class="text-decoration-none text-secondary">Stock</a> &rsaquo;
          <a routerLink="/stock/balances" class="text-decoration-none text-secondary">Balances</a> &rsaquo;
          Card
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark d-flex align-items-center gap-2">
          Stock card
          <span class="badge text-bg-light border text-secondary font-monospace">item #{{ itemId() }}</span>
        </h1>
        <p class="text-secondary mb-0 small">{{ moves().length }} move{{ moves().length === 1 ? '' : 's' }} in window.</p>
      </div>
      <a class="btn btn-outline-secondary d-inline-flex align-items-center gap-2" routerLink="/stock/balances">
        <i class="bi bi-arrow-left"></i> Back to balances
      </a>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i><span class="flex-grow-1">{{ error() }}</span>
      </div>
    }

    <div class="card border-0 shadow-sm overflow-hidden">
      @if (moves().length === 0) {
        <div class="p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-graph-up"></i></div>
          <p class="small text-secondary mb-0">No movements recorded for this item yet.</p>
        </div>
      } @else {
        <div class="table-responsive">
          <table class="table table-hover align-middle mb-0 simple-table">
            <thead>
              <tr>
                <th>When</th>
                <th>Type</th>
                <th class="text-center">Direction</th>
                <th class="text-end">Qty</th>
                <th class="text-end">Unit cost</th>
                <th>Reference</th>
              </tr>
            </thead>
            <tbody>
              @for (m of moves(); track m.id) {
                <tr>
                  <td class="small text-secondary">{{ m.at | date:'short' }}</td>
                  <td class="small">{{ m.moveType }}</td>
                  <td class="text-center">
                    @if (m.direction === 'IN') {
                      <span class="dir-pill dir-pill--in">
                        <i class="bi bi-arrow-down-circle-fill"></i> IN
                      </span>
                    } @else {
                      <span class="dir-pill dir-pill--out">
                        <i class="bi bi-arrow-up-circle-fill"></i> OUT
                      </span>
                    }
                  </td>
                  <td class="text-end fw-semibold"
                      [class.text-success]="m.direction === 'IN'"
                      [class.text-danger]="m.direction === 'OUT'">
                    {{ m.direction === 'IN' ? '+' : '−' }}{{ m.qty | number:'1.0-4' }}
                  </td>
                  <td class="text-end small text-secondary">{{ m.costAmount | number:'1.2-2' }}</td>
                  <td class="small text-secondary">
                    <span class="font-monospace">{{ m.refType }}#{{ m.refId }}</span>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.65rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }

    .dir-pill {
      display: inline-flex; align-items: center; gap: 0.25rem;
      padding: 0.2rem 0.55rem; border-radius: 999px;
      font-size: 0.72rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .dir-pill--in  { background: #d1fae5; color: #047857; }
    .dir-pill--out { background: #fee2e2; color: #b91c1c; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #e0ecff; color: #1d4ed8; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class StockCardComponent implements OnInit {
  private readonly stock = inject(StockService);
  private readonly route = inject(ActivatedRoute);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  protected readonly itemId = signal<string | null>(null);
  protected readonly moves = signal<StockMove[]>([]);
  protected readonly error = signal<string | null>(null);

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  ngOnInit(): void {
    const itemId = this.route.snapshot.paramMap.get('itemId') ?? '';
    this.itemId.set(itemId);
    const branchId = this.branchId();
    if (branchId === null) {
      this.error.set('No active branch selected.');
      return;
    }
    this.stock.stockCard(itemId, branchId, 0, 200).subscribe({
      next: page => this.moves.set(page.content),
      error: err => this.showError(err)
    });
  }

  private showError(err: unknown): void {
    if (err instanceof HttpErrorResponse) {
      const envelope = err.error as ApiResponse<unknown> | null;
      this.error.set(envelope?.message ?? `Request failed (${err.status})`);
    } else {
      this.error.set('Unexpected error');
    }
  }
}

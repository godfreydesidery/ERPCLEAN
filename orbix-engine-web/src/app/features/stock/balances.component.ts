import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { StockService } from './stock.service';
import { ItemBranchBalance } from './stock.models';

@Component({
  selector: 'orbix-stock-balances',
  standalone: true,
  imports: [RouterLink, DatePipe],
  template: `
    <h2 class="h3 mb-4">Stock balances</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    @if (branchId() === null) {
      <div class="alert alert-warning py-2">No active branch selected.</div>
    } @else {
      <p class="text-muted small">Branch {{ branchId() }}</p>
      <table class="table table-sm align-middle">
        <thead>
          <tr><th>Item</th><th class="text-end">On hand</th><th class="text-end">Reserved</th>
              <th class="text-end">In transit</th><th class="text-end">Avg cost</th>
              <th>Last moved</th><th></th></tr>
        </thead>
        <tbody>
          @for (b of balances(); track b.itemId) {
            <tr [class.table-warning]="b.reorderMin !== null && b.qtyOnHand <= b.reorderMin">
              <td>{{ b.itemId }}</td>
              <td class="text-end">{{ b.qtyOnHand }}</td>
              <td class="text-end">{{ b.qtyReserved }}</td>
              <td class="text-end">{{ b.qtyInTransit }}</td>
              <td class="text-end">{{ b.avgCost }}</td>
              <td>{{ b.lastMovedAt ? (b.lastMovedAt | date:'short') : '—' }}</td>
              <td class="text-end">
                <a class="btn btn-sm btn-outline-secondary"
                   [routerLink]="['/stock/card', b.itemId]">Stock card</a>
              </td>
            </tr>
          } @empty {
            <tr><td colspan="7" class="text-muted">No stock balances for this branch.</td></tr>
          }
        </tbody>
      </table>
    }
  `
})
export class BalancesComponent implements OnInit {
  private readonly stock = inject(StockService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly balances = signal<ItemBranchBalance[]>([]);
  readonly error = signal<string | null>(null);

  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  ngOnInit(): void {
    const branchId = this.branchId();
    if (branchId === null) return;
    this.stock.listBalances(branchId).subscribe({
      next: list => this.balances.set(list),
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

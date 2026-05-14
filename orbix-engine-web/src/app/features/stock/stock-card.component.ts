import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { StockService } from './stock.service';
import { StockMove } from './stock.models';

@Component({
  selector: 'orbix-stock-card',
  standalone: true,
  imports: [RouterLink, DatePipe],
  template: `
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h2 class="h3 mb-0">Stock card — item {{ itemId() }}</h2>
      <a class="btn btn-outline-secondary" routerLink="/stock/balances">Back to balances</a>
    </div>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <table class="table table-sm align-middle">
      <thead>
        <tr><th>At</th><th>Type</th><th>Direction</th><th class="text-end">Qty</th>
            <th class="text-end">Unit cost</th><th>Ref</th></tr>
      </thead>
      <tbody>
        @for (m of moves(); track m.id) {
          <tr>
            <td>{{ m.at | date:'short' }}</td>
            <td>{{ m.moveType }}</td>
            <td>
              @if (m.direction === 'IN') {
                <span class="badge text-bg-success">IN</span>
              } @else {
                <span class="badge text-bg-secondary">OUT</span>
              }
            </td>
            <td class="text-end">{{ m.qty }}</td>
            <td class="text-end">{{ m.costAmount }}</td>
            <td>{{ m.refType }}#{{ m.refId }}</td>
          </tr>
        } @empty {
          <tr><td colspan="6" class="text-muted">No movements for this item.</td></tr>
        }
      </tbody>
    </table>
  `
})
export class StockCardComponent implements OnInit {
  private readonly stock = inject(StockService);
  private readonly route = inject(ActivatedRoute);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  readonly itemId = signal<number | null>(null);
  readonly moves = signal<StockMove[]>([]);
  readonly error = signal<string | null>(null);

  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  ngOnInit(): void {
    const itemId = Number(this.route.snapshot.paramMap.get('itemId'));
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

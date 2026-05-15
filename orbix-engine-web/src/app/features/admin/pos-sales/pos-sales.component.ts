import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { ApiResponse, unwrap } from '../../../core/api/api-response';
import { AuthService } from '../../../core/auth/auth.service';
import { BranchService } from '../../../core/branch/branch.service';
import { environment } from '../../../../environments/environment';
import { PosSale } from './pos-sales.models';

@Component({
  selector: 'orbix-pos-sales',
  standalone: true,
  imports: [CommonModule],
  template: `
    <h2 class="h3 mb-4">POS sales</h2>
    @if (error()) { <div class="alert alert-danger py-2">{{ error() }}</div> }
    @if (info()) { <div class="alert alert-success py-2">{{ info() }}</div> }

    <div class="row g-4">
      <div class="col-12 col-lg-4">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Recent sales</div>
          <div class="list-group list-group-flush" style="max-height: 70vh; overflow-y: auto;">
            @for (s of sales(); track s.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between"
                      [class.active]="selected()?.id === s.id" (click)="select(s)">
                <span>{{ s.number }}
                  <small class="d-block text-muted">
                    till session #{{ s.tillSessionId }} · {{ s.totalAmount | number:'1.0-2' }} ·
                    {{ s.saleAt | date:'short' }}
                  </small>
                </span>
                <span class="badge align-self-center"
                      [class.text-bg-success]="s.status === 'POSTED'"
                      [class.text-bg-danger]="s.status === 'VOIDED'">{{ s.status }}</span>
              </button>
            } @empty { <div class="list-group-item text-muted">No POS sales yet.</div> }
          </div>
        </div>
      </div>

      <div class="col-12 col-lg-8">
        @if (selected(); as sale) {
          <div class="card shadow-sm">
            <div class="card-header d-flex justify-content-between align-items-center">
              <span class="fw-semibold">{{ sale.number }} — {{ sale.status }} ({{ sale.kind }})</span>
              @if (sale.status === 'POSTED') {
                <button class="btn btn-sm btn-outline-danger" [disabled]="busy()"
                        (click)="voidSale(sale)">Void (same-day)</button>
              }
            </div>
            <div class="card-body">
              <dl class="row mb-3">
                <dt class="col-sm-3">Cashier</dt>
                <dd class="col-sm-9">#{{ sale.cashierId }}
                  @if (sale.supervisorId) { · supervisor #{{ sale.supervisorId }} }
                </dd>
                <dt class="col-sm-3">Customer</dt><dd class="col-sm-9">#{{ sale.customerId }}</dd>
                <dt class="col-sm-3">Section</dt><dd class="col-sm-9">#{{ sale.sectionId }}</dd>
                <dt class="col-sm-3">Sale at</dt>
                <dd class="col-sm-9">{{ sale.saleAt | date:'short' }} (server {{ sale.serverAt | date:'short' }})</dd>
                <dt class="col-sm-3">Business date</dt><dd class="col-sm-9">{{ sale.businessDate }}</dd>
                <dt class="col-sm-3">Subtotal</dt><dd class="col-sm-9">{{ sale.subtotalAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Header discount</dt><dd class="col-sm-9">{{ sale.discountAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Tax</dt><dd class="col-sm-9">{{ sale.taxAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Total</dt>
                <dd class="col-sm-9 fw-semibold">{{ sale.totalAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Tendered</dt><dd class="col-sm-9">{{ sale.tenderedAmount | number:'1.2-2' }}</dd>
                <dt class="col-sm-3">Change</dt><dd class="col-sm-9">{{ sale.changeAmount | number:'1.2-2' }}</dd>
                @if (sale.voidedAt) {
                  <dt class="col-sm-3">Voided</dt>
                  <dd class="col-sm-9">by #{{ sale.voidedBy }} — {{ sale.voidReason }}</dd>
                }
              </dl>
              <h5 class="h6">Lines</h5>
              <table class="table table-sm align-middle">
                <thead>
                  <tr><th>#</th><th>Item</th><th class="text-end">Qty</th>
                      <th class="text-end">Price</th><th class="text-end">Disc</th>
                      <th class="text-end">Tax</th><th class="text-end">Line total</th>
                      <th class="text-end">Cost</th></tr>
                </thead>
                <tbody>
                  @for (line of sale.lines; track line.id) {
                    <tr>
                      <td>{{ line.lineNo }}</td>
                      <td>{{ line.itemId }}</td>
                      <td class="text-end">{{ line.qty }}</td>
                      <td class="text-end">{{ line.unitPrice | number:'1.2-2' }}</td>
                      <td class="text-end">{{ line.discountPct | number:'1.0-2' }}%</td>
                      <td class="text-end">{{ line.taxAmount | number:'1.2-2' }}</td>
                      <td class="text-end">{{ line.lineTotal | number:'1.2-2' }}</td>
                      <td class="text-end">{{ line.costAmount | number:'1.2-2' }}</td>
                    </tr>
                  }
                </tbody>
              </table>

              <h5 class="h6 mt-3">Payments</h5>
              <table class="table table-sm align-middle">
                <thead>
                  <tr><th>Method</th><th class="text-end">Amount</th>
                      <th>Reference</th><th>Terminal</th><th>Last 4</th></tr>
                </thead>
                <tbody>
                  @for (pay of sale.payments; track pay.id) {
                    <tr>
                      <td>{{ pay.method }}</td>
                      <td class="text-end">{{ pay.amount | number:'1.2-2' }}</td>
                      <td>{{ pay.reference ?? '—' }}</td>
                      <td>{{ pay.terminalId ?? '—' }}</td>
                      <td>{{ pay.last4 ?? '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="alert alert-secondary">
            Select a sale from the list. POS sales are pushed from the Flutter till app — this
            page is read-only.
          </div>
        }
      </div>
    </div>
  `
})
export class PosSalesComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);
  private readonly base = environment.apiUrl;

  readonly sales = signal<PosSale[]>([]);
  readonly selected = signal<PosSale | null>(null);
  readonly busy = signal<boolean>(false);
  readonly info = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  ngOnInit(): void { this.refresh(); }

  refresh(): void {
    const branchId = this.branchId();
    let params = new HttpParams();
    if (branchId != null) params = params.set('branchId', branchId);
    unwrap(this.http.get<ApiResponse<PosSale[]>>(`${this.base}/pos-sales`, { params })).subscribe({
      next: rows => this.sales.set(rows),
      error: err => this.showError(err)
    });
  }

  select(s: PosSale): void { this.selected.set(s); }

  voidSale(s: PosSale): void {
    const reason = window.prompt(`Void sale ${s.number} — reason?`);
    if (!reason || !reason.trim()) return;
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    unwrap(this.http.post<ApiResponse<PosSale>>(
      `${this.base}/pos-sales/${s.id}/void`, { reason: reason.trim() }
    )).subscribe({
      next: voided => {
        this.busy.set(false);
        this.info.set(`Sale ${voided.number} voided.`);
        this.selected.set(voided);
        this.refresh();
      },
      error: err => { this.busy.set(false); this.showError(err); }
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

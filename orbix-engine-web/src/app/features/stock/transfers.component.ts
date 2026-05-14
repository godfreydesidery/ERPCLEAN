import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../core/api/api-response';
import { StockService } from './stock.service';
import { StockTransfer } from './stock.models';

@Component({
  selector: 'orbix-stock-transfers',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2 class="h3 mb-4">Stock transfers</h2>
    @if (error()) { <div class="alert alert-danger py-2">{{ error() }}</div> }

    <div class="row g-4">
      <div class="col-12 col-lg-4">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Transfers</div>
          <div class="list-group list-group-flush">
            @for (t of transfers(); track t.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between"
                      [class.active]="selected()?.id === t.id" (click)="select(t)">
                <span>{{ t.number }}
                  <small class="d-block text-muted">{{ t.fromBranchId }} &rarr; {{ t.toBranchId }}</small></span>
                <span class="badge text-bg-secondary align-self-center">{{ t.status }}</span>
              </button>
            } @empty { <div class="list-group-item text-muted">No transfers yet.</div> }
          </div>
        </div>

        <div class="card shadow-sm mt-3">
          <div class="card-header fw-semibold">New transfer</div>
          <div class="card-body">
            <form (ngSubmit)="create()" #f="ngForm">
              <div class="mb-2">
                <label class="form-label">Number</label>
                <input class="form-control" name="num" [(ngModel)]="newNumber" required>
              </div>
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label">From branch id</label>
                  <input class="form-control" type="number" name="fb" [(ngModel)]="newFrom" required>
                </div>
                <div class="col">
                  <label class="form-label">To branch id</label>
                  <input class="form-control" type="number" name="tb" [(ngModel)]="newTo" required>
                </div>
              </div>
              <div class="mb-2">
                <label class="form-label">Item id</label>
                <input class="form-control" type="number" name="it" [(ngModel)]="newItemId" required>
              </div>
              <div class="mb-2">
                <label class="form-label">Quantity</label>
                <input class="form-control" type="number" name="qt" [(ngModel)]="newQty" required>
              </div>
              <button class="btn btn-primary w-100" [disabled]="busy() || f.invalid">Create transfer</button>
            </form>
          </div>
        </div>
      </div>

      <div class="col-12 col-lg-8">
        @if (selected(); as transfer) {
          <div class="card shadow-sm">
            <div class="card-header d-flex justify-content-between align-items-center">
              <span class="fw-semibold">{{ transfer.number }} — {{ transfer.status }}</span>
              <span class="d-flex gap-2">
                @if (transfer.status === 'DRAFT') {
                  <button class="btn btn-sm btn-outline-primary" (click)="issue(transfer)"
                          [disabled]="busy()">Issue</button>
                }
                @if (transfer.status === 'ISSUED') {
                  <button class="btn btn-sm btn-outline-primary" (click)="receive(transfer)"
                          [disabled]="busy()">Receive</button>
                }
                @if (transfer.status === 'RECEIVED') {
                  <button class="btn btn-sm btn-outline-warning" (click)="close(transfer)"
                          [disabled]="busy()">Close</button>
                }
              </span>
            </div>
            <div class="card-body">
              <table class="table table-sm align-middle">
                <thead>
                  <tr><th>Item</th><th class="text-end">Issued</th><th class="text-end">Received</th>
                      <th class="text-end">Cost</th></tr>
                </thead>
                <tbody>
                  @for (l of transfer.lines; track l.id) {
                    <tr>
                      <td>{{ l.itemId }}</td>
                      <td class="text-end">{{ l.issuedQty }}</td>
                      <td class="text-end" style="width: 140px">
                        @if (transfer.status === 'ISSUED') {
                          <input class="form-control form-control-sm text-end" type="number"
                                 [(ngModel)]="receiveDraft[l.id]">
                        } @else { {{ l.receivedQty ?? '—' }} }
                      </td>
                      <td class="text-end">{{ l.costAmount }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="text-muted">Select a transfer to issue, receive or close it.</div>
        }
      </div>
    </div>
  `
})
export class TransfersComponent implements OnInit {
  private readonly stock = inject(StockService);

  readonly transfers = signal<StockTransfer[]>([]);
  readonly selected = signal<StockTransfer | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  newNumber = '';
  newFrom: number | null = null;
  newTo: number | null = null;
  newItemId: number | null = null;
  newQty: number | null = null;
  receiveDraft: Record<number, number | null> = {};

  ngOnInit(): void {
    this.load();
  }

  select(transfer: StockTransfer): void {
    this.selected.set(transfer);
    this.receiveDraft = {};
    for (const l of transfer.lines) {
      this.receiveDraft[l.id] = l.receivedQty ?? l.issuedQty;
    }
  }

  create(): void {
    this.run(this.stock.createTransfer({
      number: this.newNumber.trim(),
      fromBranchId: Number(this.newFrom),
      toBranchId: Number(this.newTo),
      lines: [{ itemId: Number(this.newItemId), issuedQty: Number(this.newQty) }]
    }), created => {
      this.newNumber = '';
      this.newItemId = null;
      this.newQty = null;
      this.load();
      this.select(created);
    });
  }

  issue(transfer: StockTransfer): void {
    this.run(this.stock.issueTransfer(transfer.id), updated => this.refresh(updated));
  }

  receive(transfer: StockTransfer): void {
    const lines = transfer.lines
      .filter(l => this.receiveDraft[l.id] != null)
      .map(l => ({ lineId: l.id, receivedQty: Number(this.receiveDraft[l.id]) }));
    this.run(this.stock.receiveTransfer(transfer.id, { lines }), updated => this.refresh(updated));
  }

  close(transfer: StockTransfer): void {
    this.run(this.stock.closeTransfer(transfer.id), updated => this.refresh(updated));
  }

  private refresh(updated: StockTransfer): void {
    this.load();
    this.select(updated);
  }

  private run<T>(source: Observable<T>, onSuccess: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    source.subscribe({
      next: value => { this.busy.set(false); onSuccess(value); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private load(): void {
    this.stock.listTransfers().subscribe({
      next: list => this.transfers.set(list),
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

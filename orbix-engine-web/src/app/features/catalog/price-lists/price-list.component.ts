import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { CatalogService } from '../catalog.service';
import { PriceList, PriceListItem } from '../catalog.models';

@Component({
  selector: 'orbix-price-list',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <h2 class="h3 mb-4">Price lists</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <div class="row g-4">
      <!-- Price list list + create/edit -->
      <div class="col-12 col-lg-4">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Price lists</div>
          <div class="list-group list-group-flush">
            @for (list of priceLists(); track list.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between"
                      [class.active]="selected()?.id === list.id"
                      (click)="select(list)">
                <span>
                  <span class="fw-semibold">{{ list.name }}</span>
                  <small class="d-block text-muted">{{ list.code }} · {{ list.currencyCode }}</small>
                </span>
                <span>
                  @if (list.isDefault) { <span class="badge text-bg-secondary me-1">default</span> }
                  @if (list.status !== 'ACTIVE') { <span class="badge text-bg-warning">{{ list.status }}</span> }
                </span>
              </button>
            } @empty {
              <div class="list-group-item text-muted">No price lists yet.</div>
            }
          </div>
        </div>

        <div class="card shadow-sm mt-3">
          <div class="card-header fw-semibold">
            {{ editingId() ? 'Edit price list' : 'New price list' }}
          </div>
          <div class="card-body">
            <form (ngSubmit)="submitList()" #lf="ngForm">
              <div class="mb-2">
                <label class="form-label">Code</label>
                <input class="form-control" name="code" [(ngModel)]="listForm.code" required
                       [disabled]="!!editingId()">
              </div>
              <div class="mb-2">
                <label class="form-label">Name</label>
                <input class="form-control" name="name" [(ngModel)]="listForm.name" required>
              </div>
              <div class="mb-2">
                <label class="form-label">Currency</label>
                <input class="form-control" name="ccy" [(ngModel)]="listForm.currencyCode"
                       required pattern="[A-Za-z]{3}" maxlength="3">
              </div>
              <div class="row g-2 mb-2">
                <div class="col">
                  <label class="form-label">Valid from</label>
                  <input class="form-control" type="date" name="vf" [(ngModel)]="listForm.validFrom" required>
                </div>
                <div class="col">
                  <label class="form-label">Valid to</label>
                  <input class="form-control" type="date" name="vt" [(ngModel)]="listForm.validTo">
                </div>
              </div>
              <div class="form-check">
                <input class="form-check-input" type="checkbox" id="ti" name="ti"
                       [(ngModel)]="listForm.taxInclusive">
                <label class="form-check-label" for="ti">Tax inclusive</label>
              </div>
              <div class="form-check mb-3">
                <input class="form-check-input" type="checkbox" id="def" name="def"
                       [(ngModel)]="listForm.isDefault">
                <label class="form-check-label" for="def">Company default</label>
              </div>
              <div class="d-flex gap-2">
                <button class="btn btn-primary flex-grow-1" [disabled]="busy() || lf.invalid">
                  {{ editingId() ? 'Save' : 'Create' }}
                </button>
                @if (editingId()) {
                  <button type="button" class="btn btn-outline-secondary"
                          (click)="cancelListEdit()">Cancel</button>
                }
              </div>
            </form>
          </div>
        </div>
      </div>

      <!-- Selected list prices -->
      <div class="col-12 col-lg-8">
        @if (selected(); as list) {
          <div class="card shadow-sm">
            <div class="card-header d-flex justify-content-between align-items-center">
              <span class="fw-semibold">{{ list.code }} — current prices ({{ list.currencyCode }})</span>
              <span>
                <button class="btn btn-sm btn-outline-secondary me-1" (click)="editList(list)"
                        [disabled]="busy()">Edit list</button>
                @if (list.status === 'ACTIVE') {
                  <button class="btn btn-sm btn-outline-danger" (click)="archiveList(list)"
                          [disabled]="busy()">Archive</button>
                }
              </span>
            </div>
            <div class="card-body">
              <table class="table table-sm align-middle">
                <thead>
                  <tr><th>Item id</th><th>UoM id</th><th class="text-end">Price</th>
                      <th>Valid from</th></tr>
                </thead>
                <tbody>
                  @for (row of prices(); track row.id) {
                    <tr>
                      <td>{{ row.itemId }}</td>
                      <td>{{ row.uomId }}</td>
                      <td class="text-end">{{ row.price }}</td>
                      <td>{{ row.validFrom | date:'mediumDate' }}</td>
                    </tr>
                  } @empty {
                    <tr><td colspan="4" class="text-muted">No prices set on this list.</td></tr>
                  }
                </tbody>
              </table>

              <h3 class="h6 mt-3">Set a price</h3>
              <p class="text-muted small">
                Closes the item's current price the day before the effective date and opens a new one.
              </p>
              <form (ngSubmit)="submitPrice(list)" #pf="ngForm" class="row g-2 align-items-end">
                <div class="col-6 col-md-2">
                  <label class="form-label">Item id</label>
                  <input class="form-control" type="number" name="iid"
                         [(ngModel)]="priceForm.itemId" required>
                </div>
                <div class="col-6 col-md-2">
                  <label class="form-label">UoM id</label>
                  <input class="form-control" type="number" name="uid"
                         [(ngModel)]="priceForm.uomId" required>
                </div>
                <div class="col-6 col-md-2">
                  <label class="form-label">Price</label>
                  <input class="form-control" type="number" step="0.0001" name="prc"
                         [(ngModel)]="priceForm.price" required>
                </div>
                <div class="col-6 col-md-3">
                  <label class="form-label">Effective from</label>
                  <input class="form-control" type="date" name="eff"
                         [(ngModel)]="priceForm.effectiveFrom" required>
                </div>
                <div class="col-12 col-md-3">
                  <label class="form-label">Reason</label>
                  <input class="form-control" name="rsn" [(ngModel)]="priceForm.reason">
                </div>
                <div class="col-12">
                  <button class="btn btn-primary" [disabled]="busy() || pf.invalid">Set price</button>
                </div>
              </form>
            </div>
          </div>
        } @else {
          <div class="text-muted">Select a price list to view and set its prices.</div>
        }
      </div>
    </div>
  `
})
export class PriceListComponent implements OnInit {
  private readonly catalog = inject(CatalogService);

  readonly priceLists = signal<PriceList[]>([]);
  readonly selected = signal<PriceList | null>(null);
  readonly prices = signal<PriceListItem[]>([]);
  readonly editingId = signal<number | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  listForm = blankListForm();
  priceForm = blankPriceForm();

  ngOnInit(): void {
    this.loadLists();
  }

  select(list: PriceList): void {
    this.selected.set(list);
    this.loadPrices(list.id);
  }

  editList(list: PriceList): void {
    this.editingId.set(list.id);
    this.listForm = {
      code: list.code,
      name: list.name,
      currencyCode: list.currencyCode,
      validFrom: list.validFrom,
      validTo: list.validTo ?? '',
      isDefault: list.isDefault,
      taxInclusive: list.taxInclusive
    };
  }

  cancelListEdit(): void {
    this.editingId.set(null);
    this.listForm = blankListForm();
  }

  submitList(): void {
    const id = this.editingId();
    const request = {
      name: this.listForm.name.trim(),
      currencyCode: this.listForm.currencyCode.trim().toUpperCase(),
      validFrom: this.listForm.validFrom,
      validTo: this.listForm.validTo || null,
      isDefault: this.listForm.isDefault,
      taxInclusive: this.listForm.taxInclusive
    };
    const source = id
      ? this.catalog.updatePriceList(id, request)
      : this.catalog.createPriceList({ code: this.listForm.code.trim(), ...request });
    this.run(source, list => {
      this.cancelListEdit();
      this.loadLists();
      this.select(list);
    });
  }

  archiveList(list: PriceList): void {
    this.run(this.catalog.archivePriceList(list.id), () => {
      this.selected.set(null);
      this.loadLists();
    });
  }

  submitPrice(list: PriceList): void {
    this.run(this.catalog.setPrice(list.id, {
      itemId: Number(this.priceForm.itemId),
      uomId: Number(this.priceForm.uomId),
      price: Number(this.priceForm.price),
      effectiveFrom: this.priceForm.effectiveFrom,
      reason: this.priceForm.reason.trim() || null
    }), () => {
      this.priceForm = blankPriceForm();
      this.loadPrices(list.id);
    });
  }

  private loadLists(): void {
    this.catalog.listPriceLists().subscribe({
      next: lists => this.priceLists.set(lists),
      error: err => this.showError(err)
    });
  }

  private loadPrices(priceListId: number): void {
    this.catalog.listPrices(priceListId).subscribe({
      next: prices => this.prices.set(prices),
      error: err => this.showError(err)
    });
  }

  private run<T>(source: Observable<T>, onSuccess: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    source.subscribe({
      next: value => { this.busy.set(false); onSuccess(value); },
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

function blankListForm() {
  return {
    code: '', name: '', currencyCode: 'UGX',
    validFrom: new Date().toISOString().slice(0, 10), validTo: '',
    isDefault: false, taxInclusive: false
  };
}

function blankPriceForm() {
  return {
    itemId: null as number | null,
    uomId: null as number | null,
    price: null as number | null,
    effectiveFrom: new Date().toISOString().slice(0, 10),
    reason: ''
  };
}

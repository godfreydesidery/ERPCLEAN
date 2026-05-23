import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SearchSelectComponent, SearchSelectOption } from '../../../core/ui/search-select.component';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { CatalogService } from '../catalog.service';
import { BARCODE_TYPES, BarcodeType, ItemBarcode, Uom } from '../catalog.models';

@Component({
  selector: 'orbix-barcodes-panel',
  standalone: true,
  imports: [FormsModule, SearchSelectComponent],
  template: `
    <h3 class="h6 mt-4">Barcodes</h3>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <table class="table table-sm align-middle" style="max-width: 640px">
      <thead>
        <tr><th>Barcode</th><th>Type</th><th>Pack UoM</th><th class="text-end">Pack qty</th><th></th></tr>
      </thead>
      <tbody>
        @for (barcode of barcodes(); track barcode.id) {
          <tr>
            <td>{{ barcode.barcode }}</td>
            <td>{{ barcode.barcodeType }}</td>
            <td>{{ uomLabel(barcode.packUomId) }}</td>
            <td class="text-end">{{ barcode.packQty }}</td>
            <td class="text-end">
              <button type="button" class="btn btn-sm btn-outline-danger"
                      (click)="remove(barcode)" [disabled]="busy()">Remove</button>
            </td>
          </tr>
        } @empty {
          <tr><td colspan="5" class="text-muted">No barcodes.</td></tr>
        }
      </tbody>
    </table>

    <form (ngSubmit)="add()" #bf="ngForm" class="row g-2 align-items-end" style="max-width: 640px">
      <div class="col-4">
        <label class="form-label">Barcode</label>
        <input class="form-control" name="bc" [(ngModel)]="form.barcode" required>
      </div>
      <div class="col-3">
        <label class="form-label">Type</label>
        <select class="form-select" name="bt" [(ngModel)]="form.barcodeType" required>
          @for (t of barcodeTypes; track t) { <option [value]="t">{{ t }}</option> }
        </select>
      </div>
      <div class="col-2">
        <label class="form-label">Pack UoM</label>
        <orbix-search-select [options]="uomOptions()" [(ngModel)]="form.packUomId"
                             name="puom" placeholder="—"/>
      </div>
      <div class="col-2">
        <label class="form-label">Pack qty</label>
        <input class="form-control" type="number" name="pqty" [(ngModel)]="form.packQty">
      </div>
      <div class="col-1">
        <button class="btn btn-primary w-100" [disabled]="busy() || bf.invalid">Add</button>
      </div>
    </form>
  `
})
export class BarcodesPanelComponent implements OnInit {
  private readonly catalog = inject(CatalogService);

  /** Owning item's uid — barcode endpoints address the parent item by uid now. */
  readonly itemUid = input.required<string>();

  readonly barcodes = signal<ItemBarcode[]>([]);
  readonly uoms = signal<Uom[]>([]);
  readonly uomOptions = computed<SearchSelectOption[]>(
    () => this.uoms().map(u => ({ id: u.id, label: u.code })));
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  readonly barcodeTypes = BARCODE_TYPES;
  form: { barcode: string; barcodeType: BarcodeType; packUomId: string | null; packQty: number | null } = blank();

  ngOnInit(): void {
    this.catalog.listUoms().subscribe({
      next: uoms => this.uoms.set(uoms),
      error: err => this.showError(err)
    });
    this.load();
  }

  uomLabel(id: string | null): string {
    if (id == null) return '—';
    const match = this.uoms().find(u => u.id === id);
    return match ? match.code : id;
  }

  add(): void {
    this.run(this.catalog.addBarcode(this.itemUid(), {
      barcode: this.form.barcode.trim(),
      barcodeType: this.form.barcodeType,
      packUomId: this.form.packUomId,
      packQty: this.form.packQty
    }), () => {
      this.form = blank();
      this.load();
    });
  }

  remove(barcode: ItemBarcode): void {
    this.run(this.catalog.deleteBarcode(barcode.uid), () => this.load());
  }

  private load(): void {
    this.catalog.listBarcodes(this.itemUid()).subscribe({
      next: barcodes => this.barcodes.set(barcodes),
      error: err => this.showError(err)
    });
  }

  private run<T>(source: Observable<T>, onSuccess: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    source.subscribe({
      next: () => { this.busy.set(false); onSuccess(); },
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

function blank(): { barcode: string; barcodeType: BarcodeType; packUomId: string | null; packQty: number | null } {
  return { barcode: '', barcodeType: 'EAN13', packUomId: null, packQty: null };
}

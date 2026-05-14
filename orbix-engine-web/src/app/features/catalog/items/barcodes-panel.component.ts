import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { CatalogService } from '../catalog.service';
import { ItemBarcode } from '../catalog.models';

@Component({
  selector: 'orbix-barcodes-panel',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h3 class="h6 mt-4">Barcodes</h3>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <table class="table table-sm align-middle" style="max-width: 560px">
      <thead>
        <tr><th>Barcode</th><th>Pack UoM id</th><th class="text-end">Pack qty</th><th></th></tr>
      </thead>
      <tbody>
        @for (barcode of barcodes(); track barcode.id) {
          <tr>
            <td>{{ barcode.barcode }}</td>
            <td>{{ barcode.packUomId ?? '—' }}</td>
            <td class="text-end">{{ barcode.packQty }}</td>
            <td class="text-end">
              <button type="button" class="btn btn-sm btn-outline-danger"
                      (click)="remove(barcode)" [disabled]="busy()">Remove</button>
            </td>
          </tr>
        } @empty {
          <tr><td colspan="4" class="text-muted">No barcodes.</td></tr>
        }
      </tbody>
    </table>

    <form (ngSubmit)="add()" #bf="ngForm" class="row g-2 align-items-end" style="max-width: 560px">
      <div class="col-5">
        <label class="form-label">Barcode</label>
        <input class="form-control" name="bc" [(ngModel)]="form.barcode" required>
      </div>
      <div class="col-3">
        <label class="form-label">Pack UoM id</label>
        <input class="form-control" type="number" name="puom" [(ngModel)]="form.packUomId">
      </div>
      <div class="col-2">
        <label class="form-label">Pack qty</label>
        <input class="form-control" type="number" name="pqty" [(ngModel)]="form.packQty">
      </div>
      <div class="col-2">
        <button class="btn btn-primary w-100" [disabled]="busy() || bf.invalid">Add</button>
      </div>
    </form>
  `
})
export class BarcodesPanelComponent implements OnInit {
  private readonly catalog = inject(CatalogService);

  readonly itemId = input.required<number>();

  readonly barcodes = signal<ItemBarcode[]>([]);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  form: { barcode: string; packUomId: number | null; packQty: number | null } = blank();

  ngOnInit(): void {
    this.load();
  }

  add(): void {
    this.run(this.catalog.addBarcode(this.itemId(), {
      barcode: this.form.barcode.trim(),
      packUomId: this.form.packUomId,
      packQty: this.form.packQty
    }), () => {
      this.form = blank();
      this.load();
    });
  }

  remove(barcode: ItemBarcode): void {
    this.run(this.catalog.deleteBarcode(barcode.id), () => this.load());
  }

  private load(): void {
    this.catalog.listBarcodes(this.itemId()).subscribe({
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

function blank(): { barcode: string; packUomId: number | null; packQty: number | null } {
  return { barcode: '', packUomId: null, packQty: null };
}

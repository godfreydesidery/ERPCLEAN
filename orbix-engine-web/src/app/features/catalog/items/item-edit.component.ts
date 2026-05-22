import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../../core/api/api-response';
import { CatalogService } from '../catalog.service';
import { ITEM_TYPES, ItemGroup, ItemType, Uom, VatGroup, WEIGHING_UNITS, WeighingUnit } from '../catalog.models';
import { BarcodesPanelComponent } from './barcodes-panel.component';
import { PriceHistoryPanelComponent } from './price-history-panel.component';

@Component({
  selector: 'orbix-item-edit',
  standalone: true,
  imports: [DecimalPipe, FormsModule, RouterLink, BarcodesPanelComponent, PriceHistoryPanelComponent],
  template: `
    <h2 class="h3 mb-4">{{ itemUid() ? 'Edit item' : 'New item' }}</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <form (ngSubmit)="save()" #f="ngForm" class="row g-3" style="max-width: 720px">
      <div class="col-md-4">
        <label class="form-label">Code</label>
        <input class="form-control" name="code" [(ngModel)]="form.code" required
               [disabled]="!!itemUid()">
      </div>
      <div class="col-md-8">
        <label class="form-label">Name</label>
        <input class="form-control" name="name" [(ngModel)]="form.name" required>
      </div>
      <div class="col-md-6">
        <label class="form-label">Short name</label>
        <input class="form-control" name="shortName" [(ngModel)]="form.shortName">
      </div>
      <div class="col-md-6">
        <label class="form-label">Type</label>
        <select class="form-select" name="type" [(ngModel)]="form.type" required>
          @for (t of itemTypes; track t) { <option [value]="t">{{ t }}</option> }
        </select>
      </div>
      <div class="col-md-6">
        <label class="form-label">Item group</label>
        <select class="form-select" name="group" [(ngModel)]="form.itemGroupId" required>
          <option [ngValue]="null" disabled>Select a group…</option>
          @for (g of groups(); track g.id) {
            <option [ngValue]="g.id">{{ '— '.repeat(g.level - 1) }}{{ g.name }} ({{ g.code }})</option>
          }
        </select>
      </div>
      <div class="col-md-3">
        <label class="form-label">Unit of measure</label>
        <select class="form-select" name="uom" [(ngModel)]="form.uomId" required>
          <option [ngValue]="null" disabled>Select a UoM…</option>
          @for (u of uoms(); track u.id) {
            <option [ngValue]="u.id">{{ u.name }} ({{ u.code }})</option>
          }
        </select>
      </div>
      <div class="col-md-3">
        <label class="form-label">VAT group</label>
        <select class="form-select" name="vat" [(ngModel)]="form.vatGroupId" required>
          <option [ngValue]="null" disabled>Select a VAT group…</option>
          @for (v of vatGroups(); track v.id) {
            <option [ngValue]="v.id">{{ v.name }} ({{ v.code }} — {{ v.rate * 100 | number:'1.0-2' }}%)</option>
          }
        </select>
      </div>
      @if (itemUid()) {
        <div class="col-md-3">
          <label class="form-label">Min sell price</label>
          <input class="form-control" type="number" name="msp" [(ngModel)]="form.minSellPrice">
        </div>
        <div class="col-md-3 d-flex align-items-end">
          <div class="form-check">
            <input class="form-check-input" type="checkbox" id="tracked" name="tracked"
                   [(ngModel)]="form.tracked">
            <label class="form-check-label" for="tracked">Stock-tracked</label>
          </div>
        </div>

        <div class="col-12"><hr class="my-1"></div>
        <div class="col-md-3 d-flex align-items-center">
          <div class="form-check">
            <input class="form-check-input" type="checkbox" id="weighed" name="weighed"
                   [(ngModel)]="form.weighed" (ngModelChange)="onWeighedChange()">
            <label class="form-check-label" for="weighed">Weighed item</label>
          </div>
        </div>
        <div class="col-md-3">
          <label class="form-label">Weighing unit</label>
          <select class="form-select" name="wunit" [(ngModel)]="form.weighingUnit"
                  [disabled]="!form.weighed" [required]="form.weighed">
            <option [ngValue]="null">—</option>
            @for (u of weighingUnits; track u) { <option [ngValue]="u">{{ u }}</option> }
          </select>
        </div>
        <div class="col-md-3 d-flex align-items-end">
          <div class="form-check">
            <input class="form-check-input" type="checkbox" id="batch" name="batch"
                   [(ngModel)]="form.batchTracked">
            <label class="form-check-label" for="batch">Batch-tracked</label>
          </div>
        </div>
        <div class="col-12">
          <small class="text-muted">
            A weighed item needs at least one PLU or EMBEDDED_WEIGHT barcode (added below).
          </small>
        </div>
      }
      <div class="col-12 d-flex gap-2">
        <button class="btn btn-primary" [disabled]="saving() || f.invalid">
          {{ itemUid() ? 'Save changes' : 'Create item' }}
        </button>
        <a class="btn btn-outline-secondary" routerLink="/catalog/items">Cancel</a>
      </div>
    </form>

    @if (itemUid(); as uid) {
      <div style="max-width: 720px">
        <orbix-barcodes-panel [itemUid]="uid" />
        <orbix-price-history-panel [itemUid]="uid" />
      </div>
    }
  `
})
export class ItemEditComponent implements OnInit {
  private readonly catalog = inject(CatalogService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly itemUid = signal<string | null>(null);
  readonly groups = signal<ItemGroup[]>([]);
  readonly uoms = signal<Uom[]>([]);
  readonly vatGroups = signal<VatGroup[]>([]);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  form: {
    code: string;
    name: string;
    shortName: string;
    type: ItemType;
    itemGroupId: string | null;
    uomId: string | null;
    vatGroupId: string | null;
    tracked: boolean;
    minSellPrice: number | null;
    weighed: boolean;
    weighingUnit: WeighingUnit | null;
    batchTracked: boolean;
  } = {
    code: '', name: '', shortName: '', type: 'SELLABLE',
    itemGroupId: null, uomId: null, vatGroupId: null, tracked: true, minSellPrice: null,
    weighed: false, weighingUnit: null, batchTracked: false
  };

  ngOnInit(): void {
    this.catalog.listGroups().subscribe({
      next: groups => this.groups.set(groups.filter(g => g.status === 'ACTIVE')),
      error: err => this.showError(err)
    });
    this.catalog.listUoms().subscribe({
      next: uoms => this.uoms.set(uoms),
      error: err => this.showError(err)
    });
    this.catalog.listVatGroups().subscribe({
      next: vats => this.vatGroups.set(vats.filter(v => v.status === 'ACTIVE')),
      error: err => this.showError(err)
    });

    const uidParam = this.route.snapshot.paramMap.get('uid');
    if (uidParam) {
      this.itemUid.set(uidParam);
      this.catalog.getItem(uidParam).subscribe({
        next: item => {
          this.form = {
            code: item.code,
            name: item.name,
            shortName: item.shortName ?? '',
            type: item.type,
            itemGroupId: item.itemGroupId,
            uomId: item.uomId,
            vatGroupId: item.vatGroupId,
            tracked: item.tracked,
            minSellPrice: item.minSellPrice,
            weighed: item.weighed,
            weighingUnit: item.weighingUnit,
            batchTracked: item.batchTracked
          };
        },
        error: err => this.showError(err)
      });
    }
  }

  onWeighedChange(): void {
    if (!this.form.weighed) {
      this.form.weighingUnit = null;
    }
  }

  save(): void {
    this.saving.set(true);
    this.error.set(null);
    const uid = this.itemUid();
    const done = {
      next: () => this.router.navigate(['/catalog/items']),
      error: (err: unknown) => { this.saving.set(false); this.showError(err); }
    };

    if (uid) {
      this.catalog.updateItem(uid, {
        name: this.form.name.trim(),
        shortName: emptyToNull(this.form.shortName),
        type: this.form.type,
        itemGroupId: this.form.itemGroupId!,
        uomId: this.form.uomId!,
        vatGroupId: this.form.vatGroupId!,
        tracked: this.form.tracked,
        minSellPrice: this.form.minSellPrice,
        weighed: this.form.weighed,
        weighingUnit: this.form.weighed ? this.form.weighingUnit : null,
        batchTracked: this.form.batchTracked
      }).subscribe(done);
    } else {
      this.catalog.createItem({
        code: this.form.code.trim(),
        name: this.form.name.trim(),
        shortName: emptyToNull(this.form.shortName),
        type: this.form.type,
        itemGroupId: this.form.itemGroupId!,
        uomId: this.form.uomId!,
        vatGroupId: this.form.vatGroupId!
      }).subscribe(done);
    }
  }

  private showError(err: unknown): void {
    if (err instanceof HttpErrorResponse) {
      const envelope = err.error as ApiResponse<unknown> | null;
      this.error.set(envelope?.message ?? `Request failed (${err.status})`);
    } else {
      this.error.set('Unexpected error');
    }
  }

  readonly itemTypes = ITEM_TYPES;
  readonly weighingUnits = WEIGHING_UNITS;
}

function emptyToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

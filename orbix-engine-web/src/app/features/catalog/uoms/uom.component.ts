import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { CatalogService } from '../catalog.service';
import { UOM_DIMENSIONS, Uom, UomDimension } from '../catalog.models';

@Component({
  selector: 'orbix-uom',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2 class="h3 mb-4">Units of measure</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <table class="table table-sm align-middle" style="max-width: 640px">
      <thead>
        <tr><th>Code</th><th>Name</th><th>Dimension</th><th>Base</th><th></th></tr>
      </thead>
      <tbody>
        @for (uom of uoms(); track uom.id) {
          <tr>
            <td>{{ uom.code }}</td>
            <td>{{ uom.name }}</td>
            <td>{{ uom.dimension }}</td>
            <td>{{ uom.base ? 'yes' : 'no' }}</td>
            <td class="text-end">
              <button class="btn btn-sm btn-outline-secondary" (click)="edit(uom)"
                      [disabled]="busy()">Edit</button>
            </td>
          </tr>
        } @empty {
          <tr><td colspan="5" class="text-muted">No units yet.</td></tr>
        }
      </tbody>
    </table>

    <div class="card shadow-sm" style="max-width: 640px">
      <div class="card-header fw-semibold">{{ editingId() ? 'Edit unit' : 'New unit' }}</div>
      <div class="card-body">
        <form (ngSubmit)="submit()" #f="ngForm" class="row g-2">
          <div class="col-md-3">
            <label class="form-label">Code</label>
            <input class="form-control" name="code" [(ngModel)]="form.code" required
                   [disabled]="!!editingId()">
          </div>
          <div class="col-md-4">
            <label class="form-label">Name</label>
            <input class="form-control" name="name" [(ngModel)]="form.name" required>
          </div>
          <div class="col-md-3">
            <label class="form-label">Dimension</label>
            <select class="form-select" name="dim" [(ngModel)]="form.dimension" required>
              @for (d of dimensions; track d) { <option [value]="d">{{ d }}</option> }
            </select>
          </div>
          <div class="col-md-2 d-flex align-items-end">
            <div class="form-check">
              <input class="form-check-input" type="checkbox" id="base" name="base"
                     [(ngModel)]="form.base">
              <label class="form-check-label" for="base">Base</label>
            </div>
          </div>
          <div class="col-12 d-flex gap-2">
            <button class="btn btn-primary" [disabled]="busy() || f.invalid">
              {{ editingId() ? 'Save' : 'Create' }}
            </button>
            @if (editingId()) {
              <button type="button" class="btn btn-outline-secondary" (click)="cancel()">Cancel</button>
            }
          </div>
        </form>
      </div>
    </div>
  `
})
export class UomComponent implements OnInit {
  private readonly catalog = inject(CatalogService);

  readonly uoms = signal<Uom[]>([]);
  readonly editingId = signal<number | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  readonly dimensions = UOM_DIMENSIONS;
  form: { code: string; name: string; dimension: UomDimension; base: boolean } = blank();

  ngOnInit(): void {
    this.load();
  }

  edit(uom: Uom): void {
    this.editingId.set(uom.id);
    this.form = { code: uom.code, name: uom.name, dimension: uom.dimension, base: uom.base };
  }

  cancel(): void {
    this.editingId.set(null);
    this.form = blank();
  }

  submit(): void {
    const id = this.editingId();
    const request = {
      name: this.form.name.trim(),
      dimension: this.form.dimension,
      base: this.form.base
    };
    const source = id
      ? this.catalog.updateUom(id, request)
      : this.catalog.createUom({ code: this.form.code.trim(), ...request });
    this.run(source, () => {
      this.cancel();
      this.load();
    });
  }

  private load(): void {
    this.catalog.listUoms().subscribe({
      next: uoms => this.uoms.set(uoms),
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

function blank(): { code: string; name: string; dimension: UomDimension; base: boolean } {
  return { code: '', name: '', dimension: 'COUNT', base: false };
}

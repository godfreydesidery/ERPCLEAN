import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { CatalogService } from '../catalog.service';
import { UOM_DIMENSIONS, Uom, UomDimension } from '../catalog.models';

@Component({
  selector: 'orbix-uom',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
        <a routerLink=".." class="text-decoration-none text-secondary">Catalog</a> &rsaquo; Units of measure
      </p>
      <h1 class="h3 fw-bold mb-1 text-dark">Units of measure</h1>
      <p class="text-secondary mb-0 small">Register the units used for stock, sales and weighing.</p>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="error.set(null)"></button>
      </div>
    }

    <div class="row g-3 g-md-4">
      <!-- List -->
      <div class="col-12 col-lg-7">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3">
            <h2 class="h6 fw-bold mb-0 text-dark">Existing units</h2>
          </div>
          @if (uoms().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-rulers"></i></div>
              <p class="small text-secondary mb-0">No units defined yet. Add one to get started.</p>
            </div>
          } @else {
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr>
                    <th>Code</th><th>Name</th><th>Dimension</th><th>Base</th><th class="text-end"></th>
                  </tr>
                </thead>
                <tbody>
                  @for (uom of uoms(); track uom.uid) {
                    <tr [class.table-active]="editingUid() === uom.uid">
                      <td><span class="badge text-bg-light border text-secondary font-monospace">{{ uom.code }}</span></td>
                      <td class="fw-semibold text-dark">{{ uom.name }}</td>
                      <td><span class="dim-pill dim-pill--{{ uom.dimension.toLowerCase() }}">{{ uom.dimension }}</span></td>
                      <td>
                        @if (uom.base) {
                          <i class="bi bi-check-circle-fill text-success"></i>
                        } @else {
                          <span class="text-muted small">—</span>
                        }
                      </td>
                      <td class="text-end">
                        <button class="btn btn-sm btn-outline-secondary" (click)="edit(uom)" [disabled]="busy()">
                          <i class="bi bi-pencil"></i>
                        </button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      </div>

      <!-- Form -->
      <div class="col-12 col-lg-5">
        <div class="card border-0 shadow-sm">
          <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">
              {{ editingUid() ? 'Edit unit' : 'New unit' }}
            </h2>
            @if (editingUid()) {
              <span class="badge text-bg-light text-secondary">Editing #{{ editingUid() }}</span>
            }
          </div>
          <div class="card-body p-3">
            <form (ngSubmit)="submit()" #f="ngForm" class="d-flex flex-column gap-3">
              <div>
                <label class="form-label small fw-semibold text-secondary">Code</label>
                <input class="form-control" name="code" [(ngModel)]="form.code" required
                       [disabled]="!!editingUid()" placeholder="e.g. KG">
              </div>
              <div>
                <label class="form-label small fw-semibold text-secondary">Name</label>
                <input class="form-control" name="name" [(ngModel)]="form.name" required
                       placeholder="e.g. Kilogram">
              </div>
              <div>
                <label class="form-label small fw-semibold text-secondary">Dimension</label>
                <select class="form-select" name="dim" [(ngModel)]="form.dimension" required>
                  @for (d of dimensions; track d) { <option [value]="d">{{ d }}</option> }
                </select>
              </div>
              <div class="form-check">
                <input class="form-check-input" type="checkbox" id="base" name="base"
                       [(ngModel)]="form.base">
                <label class="form-check-label small" for="base">
                  Base unit of its dimension
                </label>
              </div>
              <div class="d-flex gap-2 pt-2 border-top">
                <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                        [disabled]="busy() || f.invalid">
                  @if (busy()) {
                    <span class="spinner-border spinner-border-sm"></span>
                  } @else {
                    <i class="bi" [class.bi-save]="editingUid()" [class.bi-plus-lg]="!editingUid()"></i>
                  }
                  {{ editingUid() ? 'Save changes' : 'Create unit' }}
                </button>
                @if (editingUid()) {
                  <button type="button" class="btn btn-outline-secondary" (click)="cancel()">Cancel</button>
                }
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }

    .simple-table thead th {
      font-size: 0.78rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #6b7280;
      background: #f9fafb;
      border-bottom: 1px solid #e5e7eb;
      padding: 0.75rem 1rem;
    }
    .simple-table tbody td {
      padding: 0.75rem 1rem;
      border-bottom: 1px solid #f3f4f6;
      vertical-align: middle;
    }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }
    .simple-table tr.table-active { background: #eef4ff !important; }

    .dim-pill {
      display: inline-block;
      padding: 0.2rem 0.6rem;
      border-radius: 999px;
      font-size: 0.72rem;
      font-weight: 600;
      letter-spacing: 0.03em;
    }
    .dim-pill--count  { background: #e0ecff; color: #1d4ed8; }
    .dim-pill--weight { background: #fef3c7; color: #92400e; }
    .dim-pill--volume { background: #d1fae5; color: #047857; }
    .dim-pill--length { background: #ede9fe; color: #6d28d9; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #eef2ff; color: #6366f1; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8;
      box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }
  `]
})
export class UomComponent implements OnInit {
  private readonly catalog = inject(CatalogService);

  protected readonly uoms = signal<Uom[]>([]);
  protected readonly editingUid = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly dimensions = UOM_DIMENSIONS;
  protected form: { code: string; name: string; dimension: UomDimension; base: boolean } = blank();

  ngOnInit(): void {
    this.load();
  }

  edit(uom: Uom): void {
    this.editingUid.set(uom.uid);
    this.form = { code: uom.code, name: uom.name, dimension: uom.dimension, base: uom.base };
  }

  cancel(): void {
    this.editingUid.set(null);
    this.form = blank();
  }

  submit(): void {
    const uid = this.editingUid();
    const request = {
      name: this.form.name.trim(),
      dimension: this.form.dimension,
      base: this.form.base
    };
    const source = uid
      ? this.catalog.updateUom(uid, request)
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

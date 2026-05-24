import { Component, OnInit, computed, inject, signal } from '@angular/core';
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
            <div class="d-flex align-items-center justify-content-between gap-2 mb-2">
              <h2 class="h6 fw-bold mb-0 text-dark">Existing units</h2>
              <span class="badge text-bg-light text-secondary">{{ filtered().length }}</span>
            </div>
            <div class="d-flex flex-wrap align-items-center gap-2">
              <div class="input-group input-group-sm flex-grow-1" style="min-width: 180px;">
                <span class="input-group-text bg-white"><i class="bi bi-search"></i></span>
                <input class="form-control" type="search" placeholder="Search code or name"
                       [ngModel]="search()" (ngModelChange)="onSearch($event)" name="uomSearch">
              </div>
              <div class="btn-group btn-group-sm status-filter" role="group" aria-label="Filter by status">
                <button type="button" class="btn"
                        [class.btn-primary]="statusFilter() === 'ACTIVE'"
                        [class.btn-outline-secondary]="statusFilter() !== 'ACTIVE'"
                        (click)="setStatusFilter('ACTIVE')">Active</button>
                <button type="button" class="btn"
                        [class.btn-primary]="statusFilter() === 'ARCHIVED'"
                        [class.btn-outline-secondary]="statusFilter() !== 'ARCHIVED'"
                        (click)="setStatusFilter('ARCHIVED')">
                  Archived
                  @if (archivedCount() > 0) {
                    <span class="badge rounded-pill ms-1"
                          [class.text-bg-light]="statusFilter() === 'ARCHIVED'"
                          [class.text-bg-secondary]="statusFilter() !== 'ARCHIVED'">{{ archivedCount() }}</span>
                  }
                </button>
                <button type="button" class="btn"
                        [class.btn-primary]="statusFilter() === 'ALL'"
                        [class.btn-outline-secondary]="statusFilter() !== 'ALL'"
                        (click)="setStatusFilter('ALL')">All</button>
              </div>
            </div>
          </div>
          @if (uoms().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-rulers"></i></div>
              <p class="small text-secondary mb-0">No units defined yet. Add one to get started.</p>
            </div>
          } @else if (filtered().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-search"></i></div>
              <p class="small text-secondary mb-0">No units match the current filter.</p>
            </div>
          } @else {
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr>
                    <th>Code</th><th>Name</th><th>Dimension</th><th>Base</th><th>Status</th><th class="text-end"></th>
                  </tr>
                </thead>
                <tbody>
                  @for (uom of paged(); track uom.uid) {
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
                      <td>
                        <span class="status-badge status-badge--{{ uom.status.toLowerCase() }}">
                          <span class="status-badge__dot"></span>{{ uom.status }}
                        </span>
                      </td>
                      <td class="text-end">
                        <div class="d-inline-flex gap-2 justify-content-end">
                          <button class="btn btn-sm btn-outline-secondary" (click)="edit(uom)" [disabled]="busy()" title="Edit">
                            <i class="bi bi-pencil"></i>
                          </button>
                          @if (uom.status === 'ACTIVE') {
                            <button class="btn btn-sm btn-outline-danger" (click)="archive(uom)" [disabled]="busy()">
                              <i class="bi bi-archive me-1"></i>Archive
                            </button>
                          } @else {
                            <button class="btn btn-sm btn-success" (click)="activate(uom)" [disabled]="busy()">
                              <i class="bi bi-arrow-counterclockwise me-1"></i>Restore
                            </button>
                          }
                        </div>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <div class="card-footer bg-white border-top d-flex flex-wrap align-items-center justify-content-between gap-2 p-2 px-3">
              <span class="small text-secondary">
                Showing {{ rangeStart() }}–{{ rangeEnd() }} of {{ filtered().length }}
              </span>
              <div class="d-flex align-items-center gap-2">
                <select class="form-select form-select-sm w-auto" [ngModel]="pageSize()"
                        (ngModelChange)="onPageSize($event)" name="pageSize" aria-label="Page size">
                  @for (s of pageSizes; track s) { <option [ngValue]="s">{{ s }} / page</option> }
                </select>
                <div class="btn-group btn-group-sm">
                  <button class="btn btn-outline-secondary" (click)="prev()"
                          [disabled]="currentPage() <= 1" title="Previous page">
                    <i class="bi bi-chevron-left"></i>
                  </button>
                  <span class="btn btn-outline-secondary disabled">{{ currentPage() }} / {{ totalPages() }}</span>
                  <button class="btn btn-outline-secondary" (click)="next()"
                          [disabled]="currentPage() >= totalPages()" title="Next page">
                    <i class="bi bi-chevron-right"></i>
                  </button>
                </div>
              </div>
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

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.25rem 0.625rem; border-radius: 999px;
      font-size: 0.72rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 6px; height: 6px; border-radius: 50%; }
    .status-badge--active   { background: #d1fae5; color: #047857; }
    .status-badge--active .status-badge__dot   { background: #10b981; }
    .status-badge--inactive { background: #fef3c7; color: #92400e; }
    .status-badge--inactive .status-badge__dot { background: #f59e0b; }
    .status-badge--archived { background: #f3f4f6; color: #4b5563; }
    .status-badge--archived .status-badge__dot { background: #9ca3af; }

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

  // ---- filtering + client-side pagination ----------------------------------
  protected readonly search = signal('');
  protected readonly statusFilter = signal<'ACTIVE' | 'ARCHIVED' | 'ALL'>('ACTIVE');
  protected readonly pageSizes = [10, 25, 50, 100];
  protected readonly pageSize = signal(25);
  protected readonly page = signal(1);

  protected readonly archivedCount = computed(() =>
    this.uoms().filter(u => u.status === 'ARCHIVED').length);

  protected readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const sf = this.statusFilter();
    return this.uoms().filter(u =>
      (sf === 'ALL' || u.status === sf) &&
      (q === '' || u.code.toLowerCase().includes(q) || u.name.toLowerCase().includes(q))
    );
  });

  protected readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.filtered().length / this.pageSize())));

  /** page() clamped into [1, totalPages] so the view stays valid after filtering. */
  protected readonly currentPage = computed(() =>
    Math.min(Math.max(1, this.page()), this.totalPages()));

  protected readonly paged = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize();
    return this.filtered().slice(start, start + this.pageSize());
  });

  protected readonly rangeStart = computed(() =>
    this.filtered().length === 0 ? 0 : (this.currentPage() - 1) * this.pageSize() + 1);

  protected readonly rangeEnd = computed(() =>
    Math.min(this.currentPage() * this.pageSize(), this.filtered().length));

  protected readonly dimensions = UOM_DIMENSIONS;
  protected form: { code: string; name: string; dimension: UomDimension; base: boolean } = blank();

  ngOnInit(): void {
    this.load();
  }

  onSearch(value: string): void { this.search.set(value); this.page.set(1); }
  setStatusFilter(value: 'ACTIVE' | 'ARCHIVED' | 'ALL'): void { this.statusFilter.set(value); this.page.set(1); }
  onPageSize(value: number): void { this.pageSize.set(Number(value)); this.page.set(1); }
  prev(): void { this.page.set(Math.max(1, this.currentPage() - 1)); }
  next(): void { this.page.set(Math.min(this.totalPages(), this.currentPage() + 1)); }

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

  archive(uom: Uom): void {
    this.run(this.catalog.archiveUom(uom.uid), () => {
      if (this.editingUid() === uom.uid) {
        this.cancel();
      }
      this.load();
    });
  }

  activate(uom: Uom): void {
    this.run(this.catalog.activateUom(uom.uid), () => this.load());
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

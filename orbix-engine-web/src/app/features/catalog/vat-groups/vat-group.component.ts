import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { CatalogService } from '../catalog.service';
import { VatGroup } from '../catalog.models';

@Component({
  selector: 'orbix-vat-group',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
        <a routerLink=".." class="text-decoration-none text-secondary">Catalog</a> &rsaquo; VAT groups
      </p>
      <h1 class="h3 fw-bold mb-1 text-dark">VAT groups</h1>
      <p class="text-secondary mb-0 small">Tax classes used when posting sales and procurement.</p>
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
              <h2 class="h6 fw-bold mb-0 text-dark">Existing groups</h2>
              <span class="badge text-bg-light text-secondary">{{ filteredVatGroups().length }}</span>
            </div>
            <div class="btn-group btn-group-sm" role="group" aria-label="Filter by status">
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
          @if (vatGroups().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-percent"></i></div>
              <p class="small text-secondary mb-0">No VAT groups yet. Add one to get started.</p>
            </div>
          } @else if (filteredVatGroups().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-funnel"></i></div>
              <p class="small text-secondary mb-0">No VAT groups match the current filter.</p>
            </div>
          } @else {
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr>
                    <th>Code</th><th>Name</th><th class="text-end">Rate</th>
                    <th>Valid from</th><th>Status</th><th class="text-end"></th>
                  </tr>
                </thead>
                <tbody>
                  @for (group of filteredVatGroups(); track group.uid) {
                    <tr [class.table-active]="editingUid() === group.uid">
                      <td>
                        <span class="badge text-bg-light border text-secondary font-monospace">{{ group.code }}</span>
                        @if (group.isDefault) {
                          <span class="badge text-bg-primary-subtle text-primary ms-1">DEFAULT</span>
                        }
                      </td>
                      <td class="fw-semibold text-dark">{{ group.name }}</td>
                      <td class="text-end fw-semibold">{{ (group.rate * 100).toFixed(2) }}%</td>
                      <td class="small text-secondary">{{ group.validFrom }}</td>
                      <td>
                        <span class="status-badge status-badge--{{ group.status.toLowerCase() }}">
                          <span class="status-badge__dot"></span>{{ group.status }}
                        </span>
                      </td>
                      <td class="text-end">
                        <div class="d-inline-flex gap-2 justify-content-end">
                          <button class="btn btn-sm btn-outline-secondary" (click)="edit(group)" [disabled]="busy()" title="Edit">
                            <i class="bi bi-pencil"></i>
                          </button>
                          @if (group.status === 'ACTIVE') {
                            <button class="btn btn-sm btn-outline-danger" (click)="archive(group)" [disabled]="busy()">
                              <i class="bi bi-archive me-1"></i>Archive
                            </button>
                          } @else {
                            <button class="btn btn-sm btn-success" (click)="activate(group)" [disabled]="busy()">
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
          }
        </div>
      </div>

      <!-- Form -->
      <div class="col-12 col-lg-5">
        <div class="card border-0 shadow-sm">
          <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">
              {{ editingUid() ? 'Edit VAT group' : 'New VAT group' }}
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
                       [disabled]="!!editingUid()" placeholder="e.g. STD16">
              </div>
              <div>
                <label class="form-label small fw-semibold text-secondary">Name</label>
                <input class="form-control" name="name" [(ngModel)]="form.name" required
                       placeholder="e.g. Standard rate">
              </div>
              <div class="row g-2">
                <div class="col-6">
                  <label class="form-label small fw-semibold text-secondary">Rate <span class="text-muted">(0–1)</span></label>
                  <input class="form-control" type="number" step="0.0001" min="0" max="1"
                         name="rate" [(ngModel)]="form.rate" required>
                </div>
                <div class="col-6">
                  <label class="form-label small fw-semibold text-secondary">Valid from</label>
                  <input class="form-control" type="date" name="vf" [(ngModel)]="form.validFrom" required>
                </div>
              </div>
              <div class="form-check">
                <input class="form-check-input" type="checkbox" id="def" name="def"
                       [(ngModel)]="form.isDefault">
                <label class="form-check-label small" for="def">
                  Use as company default
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
                  {{ editingUid() ? 'Save changes' : 'Create group' }}
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

    .text-bg-primary-subtle { background: #e0ecff; color: #1d4ed8; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #fef3c7; color: #b45309; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8;
      box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }
  `]
})
export class VatGroupComponent implements OnInit {
  private readonly catalog = inject(CatalogService);

  protected readonly vatGroups = signal<VatGroup[]>([]);
  protected readonly editingUid = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly statusFilter = signal<'ACTIVE' | 'ARCHIVED' | 'ALL'>('ACTIVE');
  protected readonly archivedCount = computed(() =>
    this.vatGroups().filter(g => g.status === 'ARCHIVED').length);
  protected readonly filteredVatGroups = computed(() => {
    const sf = this.statusFilter();
    return sf === 'ALL' ? this.vatGroups() : this.vatGroups().filter(g => g.status === sf);
  });

  protected form: { code: string; name: string; rate: number; validFrom: string; isDefault: boolean } = blank();

  ngOnInit(): void {
    this.load();
  }

  edit(group: VatGroup): void {
    this.editingUid.set(group.uid);
    this.form = {
      code: group.code,
      name: group.name,
      rate: group.rate,
      validFrom: group.validFrom,
      isDefault: group.isDefault
    };
  }

  cancel(): void {
    this.editingUid.set(null);
    this.form = blank();
  }

  submit(): void {
    const uid = this.editingUid();
    const request = {
      name: this.form.name.trim(),
      rate: Number(this.form.rate),
      validFrom: this.form.validFrom,
      isDefault: this.form.isDefault
    };
    const source = uid
      ? this.catalog.updateVatGroup(uid, request)
      : this.catalog.createVatGroup({ code: this.form.code.trim(), ...request });
    this.run(source, () => {
      this.cancel();
      this.load();
    });
  }

  setStatusFilter(value: 'ACTIVE' | 'ARCHIVED' | 'ALL'): void { this.statusFilter.set(value); }

  archive(group: VatGroup): void {
    this.run(this.catalog.archiveVatGroup(group.uid), () => this.load());
  }

  activate(group: VatGroup): void {
    this.run(this.catalog.activateVatGroup(group.uid), () => this.load());
  }

  private load(): void {
    this.catalog.listVatGroups().subscribe({
      next: groups => this.vatGroups.set(groups),
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

function blank(): { code: string; name: string; rate: number; validFrom: string; isDefault: boolean } {
  return { code: '', name: '', rate: 0, validFrom: new Date().toISOString().slice(0, 10), isDefault: false };
}

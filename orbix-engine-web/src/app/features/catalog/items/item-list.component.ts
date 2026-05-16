import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../../core/api/api-response';
import { HasPermissionDirective } from '../../../core/auth/has-permission.directive';
import { CatalogService } from '../catalog.service';
import { Item, ItemStatus, Page } from '../catalog.models';

interface StatusFilterOption {
  label: string;
  value: ItemStatus | null;
}

@Component({
  selector: 'orbix-item-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DecimalPipe, HasPermissionDirective],
  template: `
    <!-- Page header -->
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Catalog</a> &rsaquo; Items
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Items</h1>
        <p class="text-secondary mb-0 small">{{ page().totalElements }} item{{ page().totalElements === 1 ? '' : 's' }} on file.</p>
      </div>
      <a class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" routerLink="new"
         *orbixHasPermission="'ITEM.CREATE'">
        <i class="bi bi-plus-lg"></i> New item
      </a>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="error.set(null)"></button>
      </div>
    }

    <!-- Toolbar -->
    <div class="card border-0 shadow-sm mb-3">
      <div class="card-body p-3 d-flex flex-wrap align-items-center gap-3">
        <div class="search-box flex-grow-1">
          <i class="bi bi-search"></i>
          <input type="search" class="form-control" placeholder="Search by code or name"
                 [(ngModel)]="searchTerm" (ngModelChange)="onSearchChange()">
        </div>
        <div class="status-pills d-flex gap-1 flex-wrap">
          @for (opt of statusOptions; track opt.value) {
            <button type="button" class="status-pill"
                    [class.is-active]="statusFilter === opt.value"
                    (click)="setStatus(opt.value)">
              {{ opt.label }}
            </button>
          }
        </div>
      </div>
    </div>

    <!-- Items table / card list -->
    <div class="card border-0 shadow-sm overflow-hidden">
      @if (busy() && page().content.length === 0) {
        <div class="p-5 text-center text-secondary">
          <div class="spinner-border spinner-border-sm me-2" role="status"></div>
          Loading items…
        </div>
      } @else if (filteredItems().length === 0) {
        <div class="p-5 text-center">
          <div class="empty-icon mx-auto mb-3"><i class="bi bi-tag"></i></div>
          <h2 class="h6 fw-bold mb-1 text-dark">No items match</h2>
          <p class="small text-secondary mb-3">
            @if (searchTerm) {
              Nothing matched <strong>"{{ searchTerm }}"</strong>. Try a different code or name.
            } @else {
              Add your first catalog item to start trading.
            }
          </p>
          @if (!searchTerm) {
            <a class="btn btn-sm btn-primary" routerLink="new" *orbixHasPermission="'ITEM.CREATE'">
              <i class="bi bi-plus-lg me-1"></i> New item
            </a>
          }
        </div>
      } @else {
        <!-- Desktop / tablet table -->
        <div class="table-responsive d-none d-md-block">
          <table class="table table-hover align-middle mb-0 item-table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>Type</th>
                <th>Status</th>
                <th class="text-end">Avg cost</th>
                <th class="text-end">Last cost</th>
                <th class="text-end actions-col">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (item of filteredItems(); track item.id) {
                <tr>
                  <td><span class="badge text-bg-light border text-secondary font-monospace">{{ item.code }}</span></td>
                  <td class="fw-semibold text-dark">{{ item.name }}</td>
                  <td><span class="small text-secondary">{{ item.type }}</span></td>
                  <td>
                    <span class="status-badge status-badge--{{ item.status.toLowerCase() }}">
                      <span class="status-badge__dot"></span>{{ item.status }}
                    </span>
                  </td>
                  <td class="text-end text-secondary">{{ item.avgCost | number:'1.2-2' }}</td>
                  <td class="text-end text-secondary">{{ item.lastCost | number:'1.2-2' }}</td>
                  <td class="text-end actions-col">
                    <div class="btn-group btn-group-sm" role="group">
                      <a class="btn btn-outline-secondary" [routerLink]="[item.id, 'edit']" title="Edit">
                        <i class="bi bi-pencil"></i>
                      </a>
                      @if (item.status === 'ARCHIVED') {
                        <button class="btn btn-outline-success" (click)="activate(item)"
                                [disabled]="busy()" title="Activate"
                                *orbixHasPermission="'ITEM.UPDATE'">
                          <i class="bi bi-arrow-counterclockwise"></i>
                        </button>
                      } @else {
                        <button class="btn btn-outline-danger" (click)="archive(item)"
                                [disabled]="busy()" title="Archive"
                                *orbixHasPermission="'ITEM.ARCHIVE'">
                          <i class="bi bi-archive"></i>
                        </button>
                      }
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <!-- Mobile card list -->
        <ul class="list-unstyled mb-0 d-md-none">
          @for (item of filteredItems(); track item.id) {
            <li class="item-card">
              <div class="d-flex justify-content-between align-items-start gap-2 mb-1">
                <div class="flex-grow-1">
                  <span class="badge text-bg-light border text-secondary font-monospace mb-1">{{ item.code }}</span>
                  <p class="fw-semibold text-dark mb-0">{{ item.name }}</p>
                </div>
                <span class="status-badge status-badge--{{ item.status.toLowerCase() }}">
                  <span class="status-badge__dot"></span>{{ item.status }}
                </span>
              </div>
              <div class="d-flex justify-content-between align-items-center small text-secondary mb-2">
                <span>{{ item.type }}</span>
                <span>Avg {{ item.avgCost | number:'1.2-2' }}</span>
              </div>
              <div class="btn-group btn-group-sm w-100" role="group">
                <a class="btn btn-outline-secondary" [routerLink]="[item.id, 'edit']">
                  <i class="bi bi-pencil me-1"></i>Edit
                </a>
                @if (item.status === 'ARCHIVED') {
                  <button class="btn btn-outline-success" (click)="activate(item)"
                          [disabled]="busy()" *orbixHasPermission="'ITEM.UPDATE'">
                    <i class="bi bi-arrow-counterclockwise me-1"></i>Activate
                  </button>
                } @else {
                  <button class="btn btn-outline-danger" (click)="archive(item)"
                          [disabled]="busy()" *orbixHasPermission="'ITEM.ARCHIVE'">
                    <i class="bi bi-archive me-1"></i>Archive
                  </button>
                }
              </div>
            </li>
          }
        </ul>
      }

      <!-- Pagination footer -->
      @if (filteredItems().length > 0) {
        <div class="card-footer bg-white border-top d-flex flex-wrap justify-content-between align-items-center gap-2 p-3">
          <small class="text-secondary">
            Page {{ page().page + 1 }} of {{ page().totalPages || 1 }} · {{ page().totalElements }} item(s)
          </small>
          <div class="btn-group">
            <button class="btn btn-sm btn-outline-secondary d-inline-flex align-items-center gap-1"
                    [disabled]="page().page === 0 || busy()"
                    (click)="goTo(page().page - 1)">
              <i class="bi bi-chevron-left"></i> Prev
            </button>
            <button class="btn btn-sm btn-outline-secondary d-inline-flex align-items-center gap-1"
                    [disabled]="page().page + 1 >= page().totalPages || busy()"
                    (click)="goTo(page().page + 1)">
              Next <i class="bi bi-chevron-right"></i>
            </button>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }

    /* ---- Search box ---- */
    .search-box {
      position: relative;
      min-width: 220px;
    }
    .search-box i {
      position: absolute;
      left: 0.875rem;
      top: 50%;
      transform: translateY(-50%);
      color: #9ca3af;
      pointer-events: none;
    }
    .search-box .form-control {
      padding-left: 2.4rem;
      border: 1px solid #e5e7eb;
    }
    .search-box .form-control:focus {
      border-color: #1d4ed8;
      box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }

    /* ---- Status pills ---- */
    .status-pill {
      padding: 0.4rem 0.85rem;
      font-size: 0.85rem;
      font-weight: 500;
      border: 1px solid #e5e7eb;
      border-radius: 999px;
      background: #fff;
      color: #6b7280;
      transition: all 0.15s ease;
    }
    .status-pill:hover { border-color: #cbd5e1; color: #1f2937; }
    .status-pill.is-active {
      background: #0d2a5b;
      border-color: #0d2a5b;
      color: #fff;
    }

    /* ---- Item table ---- */
    .item-table thead th {
      font-size: 0.78rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #6b7280;
      background: #f9fafb;
      border-bottom: 1px solid #e5e7eb;
      padding: 0.75rem 1rem;
    }
    .item-table tbody td {
      padding: 0.875rem 1rem;
      border-bottom: 1px solid #f3f4f6;
      vertical-align: middle;
    }
    .item-table tbody tr:last-child td { border-bottom: none; }
    .item-table tbody tr:hover { background: #f8fafc; }
    .item-table .actions-col { width: 1%; white-space: nowrap; }

    /* ---- Status badges ---- */
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      padding: 0.25rem 0.625rem;
      border-radius: 999px;
      font-size: 0.72rem;
      font-weight: 600;
      letter-spacing: 0.03em;
    }
    .status-badge__dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }
    .status-badge--active   { background: #d1fae5; color: #047857; }
    .status-badge--active .status-badge__dot   { background: #10b981; }
    .status-badge--inactive { background: #fef3c7; color: #92400e; }
    .status-badge--inactive .status-badge__dot { background: #f59e0b; }
    .status-badge--archived { background: #f3f4f6; color: #4b5563; }
    .status-badge--archived .status-badge__dot { background: #9ca3af; }

    /* ---- Mobile cards ---- */
    .item-card {
      padding: 1rem;
      border-bottom: 1px solid #f3f4f6;
    }
    .item-card:last-child { border-bottom: none; }

    /* ---- Empty state ---- */
    .empty-icon {
      width: 64px;
      height: 64px;
      border-radius: 16px;
      background: #eef2ff;
      color: #6366f1;
      font-size: 1.75rem;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    @media (max-width: 575.98px) {
      .search-box { min-width: 100%; }
      .status-pills { width: 100%; overflow-x: auto; flex-wrap: nowrap; }
      .status-pill { flex-shrink: 0; }
    }

    @media (prefers-reduced-motion: reduce) {
      .status-pill { transition: none; }
    }
  `]
})
export class ItemListComponent implements OnInit {
  private readonly catalog = inject(CatalogService);

  private static readonly PAGE_SIZE = 20;
  private static readonly EMPTY: Page<Item> = {
    content: [], page: 0, size: 20, totalElements: 0, totalPages: 0
  };

  protected readonly page = signal<Page<Item>>(ItemListComponent.EMPTY);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected statusFilter: ItemStatus | null = null;
  protected searchTerm = '';

  protected readonly statusOptions: StatusFilterOption[] = [
    { label: 'All',      value: null },
    { label: 'Active',   value: 'ACTIVE' },
    { label: 'Inactive', value: 'INACTIVE' },
    { label: 'Archived', value: 'ARCHIVED' },
  ];

  private readonly searchSignal = signal('');

  protected readonly filteredItems = computed(() => {
    const items = this.page().content;
    const q = this.searchSignal().trim().toLowerCase();
    if (!q) return items;
    return items.filter(it =>
      it.code.toLowerCase().includes(q) ||
      it.name.toLowerCase().includes(q)
    );
  });

  ngOnInit(): void {
    this.goTo(0);
  }

  setStatus(value: ItemStatus | null): void {
    if (this.statusFilter === value) return;
    this.statusFilter = value;
    this.goTo(0);
  }

  onSearchChange(): void {
    this.searchSignal.set(this.searchTerm);
  }

  goTo(pageIndex: number): void {
    this.busy.set(true);
    this.error.set(null);
    this.catalog.listItems(this.statusFilter, pageIndex, ItemListComponent.PAGE_SIZE).subscribe({
      next: result => {
        this.page.set(result);
        this.busy.set(false);
      },
      error: err => {
        this.busy.set(false);
        this.showError(err);
      }
    });
  }

  archive(item: Item): void {
    this.busy.set(true);
    this.catalog.archiveItem(item.id).subscribe({
      next: () => this.goTo(this.page().page),
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  activate(item: Item): void {
    this.busy.set(true);
    this.catalog.activateItem(item.id).subscribe({
      next: () => this.goTo(this.page().page),
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

import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../../core/api/api-response';
import { HasPermissionDirective } from '../../../core/auth/has-permission.directive';
import { CatalogService } from '../catalog.service';
import { Item, ItemStatus, Page } from '../catalog.models';

@Component({
  selector: 'orbix-item-list',
  standalone: true,
  imports: [FormsModule, RouterLink, HasPermissionDirective],
  template: `
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h2 class="h3 mb-0">Items</h2>
      <a class="btn btn-primary" routerLink="new" *orbixHasPermission="'ITEM.CREATE'">New item</a>
    </div>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <div class="mb-3">
      <select class="form-select form-select-sm w-auto" [(ngModel)]="statusFilter"
              (change)="reload()">
        <option [ngValue]="null">All statuses</option>
        <option value="ACTIVE">Active</option>
        <option value="INACTIVE">Inactive</option>
        <option value="ARCHIVED">Archived</option>
      </select>
    </div>

    <table class="table table-sm align-middle">
      <thead>
        <tr><th>Code</th><th>Name</th><th>Type</th><th>Status</th><th class="text-end">Avg cost</th><th></th></tr>
      </thead>
      <tbody>
        @for (item of page().content; track item.id) {
          <tr>
            <td>{{ item.code }}</td>
            <td>{{ item.name }}</td>
            <td>{{ item.type }}</td>
            <td>
              @if (item.status === 'ACTIVE') {
                <span class="badge text-bg-success">ACTIVE</span>
              } @else {
                <span class="badge text-bg-secondary">{{ item.status }}</span>
              }
            </td>
            <td class="text-end">{{ item.avgCost }}</td>
            <td class="text-end">
              <a class="btn btn-sm btn-outline-secondary me-1" [routerLink]="[item.id, 'edit']">Edit</a>
              @if (item.status === 'ARCHIVED') {
                <button class="btn btn-sm btn-outline-success" (click)="activate(item)"
                        [disabled]="busy()" *orbixHasPermission="'ITEM.UPDATE'">Activate</button>
              } @else {
                <button class="btn btn-sm btn-outline-danger" (click)="archive(item)"
                        [disabled]="busy()" *orbixHasPermission="'ITEM.ARCHIVE'">Archive</button>
              }
            </td>
          </tr>
        } @empty {
          <tr><td colspan="6" class="text-muted">No items.</td></tr>
        }
      </tbody>
    </table>

    <div class="d-flex justify-content-between align-items-center">
      <small class="text-muted">{{ page().totalElements }} item(s)</small>
      <div class="btn-group">
        <button class="btn btn-sm btn-outline-secondary" [disabled]="page().page === 0 || busy()"
                (click)="goTo(page().page - 1)">Prev</button>
        <span class="btn btn-sm btn-outline-secondary disabled">
          {{ page().page + 1 }} / {{ page().totalPages || 1 }}
        </span>
        <button class="btn btn-sm btn-outline-secondary"
                [disabled]="page().page + 1 >= page().totalPages || busy()"
                (click)="goTo(page().page + 1)">Next</button>
      </div>
    </div>
  `
})
export class ItemListComponent implements OnInit {
  private readonly catalog = inject(CatalogService);

  private static readonly PAGE_SIZE = 20;
  private static readonly EMPTY: Page<Item> = {
    content: [], page: 0, size: 20, totalElements: 0, totalPages: 0
  };

  readonly page = signal<Page<Item>>(ItemListComponent.EMPTY);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  statusFilter: ItemStatus | null = null;

  ngOnInit(): void {
    this.goTo(0);
  }

  reload(): void {
    this.goTo(0);
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

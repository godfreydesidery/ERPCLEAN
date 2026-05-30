import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuditService } from './audit.service';
import { AuditFilters, AuditIntegrityResult, AuditLogRow } from './audit.models';
import { PagerComponent } from '../../../core/ui/pager.component';
import { UserPickerComponent, UserSelectedEvent } from '../../../core/ui/user-picker.component';
import { BranchPickerComponent, BranchSelectedEvent } from '../../../core/ui/branch-picker.component';
import { PriceListPickerComponent, PriceListSelectedEvent } from '../../../core/ui/price-list-picker.component';
import { SectionPickerComponent } from '../../../core/ui/section-picker.component';
import { ItemTypeaheadComponent, ItemSelectedEvent } from '../../procurement/item-typeahead.component';
import { SupplierTypeaheadComponent, SupplierSelectedEvent } from '../../procurement/supplier-typeahead.component';
import { CustomerTypeaheadComponent, CustomerSelectedEvent } from '../../sales/customer-typeahead.component';

/**
 * Entity types that have a dedicated picker. For all others the entity-id
 * field stays hidden (not a raw text box).
 */
const PICKER_ENTITY_TYPES = new Set([
  'Item', 'Customer', 'Supplier', 'Branch', 'AppUser', 'User', 'PriceList', 'Section'
]);

@Component({
  selector: 'orbix-audit-log',
  standalone: true,
  imports: [
    CommonModule, FormsModule, DatePipe, PagerComponent,
    UserPickerComponent, BranchPickerComponent, PriceListPickerComponent,
    SectionPickerComponent, ItemTypeaheadComponent,
    SupplierTypeaheadComponent, CustomerTypeaheadComponent,
  ],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Security</p>
      <h1 class="h3 fw-bold mb-1 text-dark">Audit log</h1>
      <p class="text-secondary mb-0 small">Every recorded action, newest first. Hash-chained and verifiable.</p>
    </header>

    <div class="card border-0 shadow-sm mb-3">
      <div class="card-body">
        <div class="row g-2 align-items-end">
          <div class="col-6 col-md-2">
            <label class="form-label small mb-1">Action</label>
            <input class="form-control form-control-sm" [(ngModel)]="filters.action" placeholder="LOGIN…">
          </div>
          <div class="col-6 col-md-2">
            <label class="form-label small mb-1">Entity type</label>
            <input class="form-control form-control-sm"
                   [(ngModel)]="entityTypeInput"
                   (change)="onEntityTypeChange()"
                   placeholder="Item, Branch…">
          </div>

          <!-- Entity-id: picker when entity type is known; hint otherwise -->
          <div class="col-6 col-md-2">
            @if (entityPickerType() === 'Item') {
              <orbix-item-typeahead
                instanceId="audit-entity-item"
                (itemSelected)="onEntityItem($event)"
                (itemCleared)="filters.entityId = undefined">
              </orbix-item-typeahead>
            } @else if (entityPickerType() === 'Customer') {
              <orbix-customer-typeahead
                instanceId="audit-entity-customer"
                (customerSelected)="onEntityCustomer($event)"
                (customerCleared)="filters.entityId = undefined">
              </orbix-customer-typeahead>
            } @else if (entityPickerType() === 'Supplier') {
              <orbix-supplier-typeahead
                instanceId="audit-entity-supplier"
                (supplierSelected)="onEntitySupplier($event)"
                (supplierCleared)="filters.entityId = undefined">
              </orbix-supplier-typeahead>
            } @else if (entityPickerType() === 'Branch') {
              <orbix-branch-picker
                instanceId="audit-entity-branch"
                label="Entity (branch)"
                [required]="false"
                (branchSelected)="onEntityBranch($event)"
                (branchCleared)="filters.entityId = undefined">
              </orbix-branch-picker>
            } @else if (entityPickerType() === 'AppUser' || entityPickerType() === 'User') {
              <orbix-user-picker
                instanceId="audit-entity-user"
                label="Entity (user)"
                [required]="false"
                (userSelected)="onEntityUser($event)"
                (userCleared)="filters.entityId = undefined">
              </orbix-user-picker>
            } @else if (entityPickerType() === 'PriceList') {
              <orbix-price-list-picker
                instanceId="audit-entity-pricelist"
                label="Entity (price list)"
                [required]="false"
                (priceListSelected)="onEntityPriceList($event)"
                (priceListCleared)="filters.entityId = undefined">
              </orbix-price-list-picker>
            } @else if (entityPickerType() === 'Section') {
              <!-- Section needs a branch uid — not available as a standalone filter;
                   show a disabled placeholder with a hint. -->
              <div>
                <label class="form-label small mb-1">Entity id (section)</label>
                <p class="small text-secondary mb-0">
                  <i class="bi bi-info-circle me-1"></i>Section filter requires a branch context — filter by action or entity type instead.
                </p>
              </div>
            } @else if (entityTypeInput && !hasEntityPicker()) {
              <div>
                <label class="form-label small mb-1">Entity id</label>
                <p class="small text-secondary mb-0">
                  <i class="bi bi-info-circle me-1"></i>Select a supported entity type to filter by entity.
                </p>
              </div>
            } @else if (!entityTypeInput) {
              <div>
                <label class="form-label small mb-1">Entity id</label>
                <p class="small text-secondary mb-0">Enter an entity type first.</p>
              </div>
            }
          </div>

          <!-- Actor id: user-picker -->
          <div class="col-6 col-md-2">
            <orbix-user-picker
              instanceId="audit-actor"
              label="Actor"
              [required]="false"
              (userSelected)="onActorSelected($event)"
              (userCleared)="filters.actorId = undefined">
            </orbix-user-picker>
          </div>

          <div class="col-6 col-md-2">
            <label class="form-label small mb-1">From</label>
            <input type="datetime-local" class="form-control form-control-sm" [(ngModel)]="filters.from">
          </div>
          <div class="col-6 col-md-2">
            <label class="form-label small mb-1">To</label>
            <input type="datetime-local" class="form-control form-control-sm" [(ngModel)]="filters.to">
          </div>
        </div>
        <div class="d-flex gap-2 mt-3">
          <button class="btn btn-primary btn-sm" (click)="search(0)">Search</button>
          <button class="btn btn-outline-secondary btn-sm" (click)="reset()">Reset</button>
          <button class="btn btn-outline-dark btn-sm ms-auto" (click)="runIntegrity()" [disabled]="checking()">
            {{ checking() ? 'Checking…' : 'Verify integrity' }}
          </button>
        </div>
        @if (integrity(); as r) {
          <div class="alert mt-3 mb-0 py-2 px-3 small" [class.alert-success]="r.ok" [class.alert-danger]="!r.ok">
            <i class="bi" [class.bi-shield-check]="r.ok" [class.bi-shield-exclamation]="!r.ok"></i>
            {{ r.message }}
          </div>
        }
      </div>
    </div>

    @if (error()) {
      <div class="alert alert-danger py-2 px-3 small">{{ error() }}</div>
    }

    <div class="card border-0 shadow-sm">
      <div class="table-responsive">
        <table class="table table-sm table-hover align-middle mb-0">
          <thead class="table-light">
            <tr>
              <th class="small">When</th>
              <th class="small">Actor</th>
              <th class="small">Action</th>
              <th class="small">Entity</th>
              <th class="small">Meta</th>
            </tr>
          </thead>
          <tbody>
            @for (row of rows(); track row.id) {
              <tr>
                <td class="small text-nowrap">{{ row.at | date:'medium' }}</td>
                <td class="small">{{ row.actorId }}</td>
                <td class="small"><span class="badge text-bg-light border">{{ row.action }}</span></td>
                <td class="small">{{ row.entityType }} #{{ row.entityId }}</td>
                <td class="small text-muted text-truncate" style="max-width:280px;">{{ row.metaJson }}</td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="text-center text-secondary small py-4">No audit rows match.</td></tr>
            }
          </tbody>
        </table>
      </div>
      @if (totalPages() >= 1) {
        <div class="card-footer">
          <orbix-pager [page]="page()" [totalPages]="totalPages()"
                       [totalElements]="total()" [pageSize]="size"
                       (pageChange)="search($event)"/>
        </div>
      }
    </div>
  `
})
export class AuditLogComponent implements OnInit {
  private readonly api = inject(AuditService);

  protected filters: AuditFilters = {};

  /** Raw entity-type text the operator typed (drives picker switching). */
  protected entityTypeInput = '';

  /** Normalised entity type used to select which picker to render. */
  protected readonly entityPickerType = signal<string>('');

  protected readonly rows = signal<AuditLogRow[]>([]);
  protected readonly page = signal(0);
  protected readonly total = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly error = signal<string | null>(null);
  protected readonly checking = signal(false);
  protected readonly integrity = signal<AuditIntegrityResult | null>(null);

  protected readonly size = 50;

  ngOnInit(): void {
    this.search(0);
  }

  onEntityTypeChange(): void {
    const trimmed = this.entityTypeInput.trim();
    this.filters.entityType = trimmed || undefined;
    this.filters.entityId = undefined;
    this.entityPickerType.set(trimmed);
  }

  hasEntityPicker(): boolean {
    return PICKER_ENTITY_TYPES.has(this.entityPickerType());
  }

  // --- Entity-id picker handlers ---
  onEntityItem(evt: ItemSelectedEvent): void { this.filters.entityId = evt.id; }
  onEntityCustomer(evt: CustomerSelectedEvent): void { this.filters.entityId = evt.id; }
  onEntitySupplier(evt: SupplierSelectedEvent): void { this.filters.entityId = evt.id; }
  onEntityBranch(evt: BranchSelectedEvent): void { this.filters.entityId = evt.id; }
  onEntityUser(evt: UserSelectedEvent): void { this.filters.entityId = evt.id; }
  onEntityPriceList(evt: PriceListSelectedEvent): void { this.filters.entityId = evt.id; }

  // --- Actor-id picker handler ---
  onActorSelected(evt: UserSelectedEvent): void { this.filters.actorId = evt.id; }

  search(page: number): void {
    this.error.set(null);
    this.api.list(this.normalised(), page, this.size).subscribe({
      next: p => {
        this.rows.set(p.content);
        this.page.set(p.page);
        this.total.set(p.totalElements);
        this.totalPages.set(p.totalPages);
      },
      error: () => this.error.set('Failed to load audit rows.')
    });
  }

  reset(): void {
    this.filters = {};
    this.entityTypeInput = '';
    this.entityPickerType.set('');
    this.integrity.set(null);
    this.search(0);
  }

  runIntegrity(): void {
    this.checking.set(true);
    this.api.verify(toIso(this.filters.from), toIso(this.filters.to)).subscribe({
      next: r => { this.integrity.set(r); this.checking.set(false); },
      error: () => {
        this.integrity.set({ ok: false, verifiedCount: 0, firstBrokenId: null, message: 'Integrity check failed.' });
        this.checking.set(false);
      }
    });
  }

  // datetime-local yields "2026-05-23T10:00"; the API wants ISO instants.
  private normalised(): AuditFilters {
    return { ...this.filters, from: toIso(this.filters.from), to: toIso(this.filters.to) };
  }
}

function toIso(local?: string): string | undefined {
  if (!local) return undefined;
  const d = new Date(local);
  return isNaN(d.getTime()) ? undefined : d.toISOString();
}

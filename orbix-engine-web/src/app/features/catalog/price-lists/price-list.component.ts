import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { Currency, CurrencyService } from '../../../core/currency/currency.service';
import { SearchSelectComponent, SearchSelectOption } from '../../../core/ui/search-select.component';
import { CatalogService } from '../catalog.service';
import { Item, PriceList, PriceListItem, Uom } from '../catalog.models';

@Component({
  selector: 'orbix-price-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe, SearchSelectComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Catalog</a> &rsaquo; Price lists
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Price lists</h1>
        <p class="text-secondary mb-0 small">{{ priceLists().length }} price book{{ priceLists().length === 1 ? '' : 's' }} configured.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="startNew()">
        <i class="bi bi-plus-lg"></i> New price list
      </button>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="error.set(null)"></button>
      </div>
    }
    @if (notice()) {
      <div class="alert alert-success d-flex align-items-center gap-2 py-2">
        <i class="bi bi-check-circle-fill"></i>
        <span class="flex-grow-1">{{ notice() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="notice.set(null)"></button>
      </div>
    }

    <div class="row g-3 g-md-4">
      <!-- Price list selector -->
      <div class="col-12 col-lg-4">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3">
            <div class="d-flex align-items-center justify-content-between gap-2 mb-2">
              <h2 class="h6 fw-bold mb-0 text-dark">Price books</h2>
              <span class="badge text-bg-light text-secondary">{{ filteredPriceLists().length }}</span>
            </div>
            <div class="btn-group btn-group-sm w-100" role="group" aria-label="Filter by status">
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
          @if (priceLists().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cash-stack"></i></div>
              <p class="small text-secondary mb-0">No price lists yet.</p>
            </div>
          } @else if (filteredPriceLists().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-funnel"></i></div>
              <p class="small text-secondary mb-0">No price lists match the current filter.</p>
            </div>
          } @else {
            <ul class="list-unstyled mb-0">
              @for (list of filteredPriceLists(); track list.id) {
                <li>
                  <button type="button" class="pl-row"
                          [class.is-active]="selected()?.id === list.id"
                          (click)="select(list)">
                    <div class="flex-grow-1 min-w-0">
                      <p class="fw-semibold text-dark mb-0 text-truncate">{{ list.name }}</p>
                      <p class="small text-secondary mb-0">
                        <span class="font-monospace">{{ list.code }}</span> · {{ list.currencyCode }}
                        @if (list.taxInclusive) { · tax-incl }
                      </p>
                    </div>
                    <div class="d-flex flex-column align-items-end gap-1">
                      @if (list.isDefault) {
                        <span class="badge text-bg-primary-subtle text-primary">DEFAULT</span>
                      }
                      <span class="status-badge status-badge--{{ list.status.toLowerCase() }}">
                        <span class="status-badge__dot"></span>{{ list.status }}
                      </span>
                    </div>
                  </button>
                </li>
              }
            </ul>
          }
        </div>
      </div>

      <!-- Detail / editor -->
      <div class="col-12 col-lg-8">
        @if (mode() === 'view' && !selected()) {
          <div class="card border-0 shadow-sm">
            <div class="card-body p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cursor"></i></div>
              <h2 class="h6 fw-bold mb-1 text-dark">Pick a price book</h2>
              <p class="small text-secondary mb-3">Or create a new one to start setting prices.</p>
              <button class="btn btn-sm btn-primary mx-auto" (click)="startNew()">
                <i class="bi bi-plus-lg me-1"></i> New price list
              </button>
            </div>
          </div>
        } @else if (mode() === 'create' || mode() === 'edit-list') {
          <!-- Create / edit price list form -->
          <div class="card border-0 shadow-sm">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h2 class="h6 fw-bold mb-0 text-dark">
                {{ mode() === 'create' ? 'New price list' : 'Edit price list' }}
              </h2>
              <button class="btn-close btn-sm" (click)="cancelListEdit()" aria-label="Close"></button>
            </div>
            <div class="card-body p-3">
              <form (ngSubmit)="submitList()" #lf="ngForm" class="d-flex flex-column gap-3">
                <div class="row g-2">
                  <div class="col-md-4">
                    <label class="form-label small fw-semibold text-secondary">Code</label>
                    <input class="form-control" name="code" [(ngModel)]="listForm.code" required
                           [disabled]="mode() === 'edit-list'" placeholder="e.g. RETAIL">
                  </div>
                  <div class="col-md-8">
                    <label class="form-label small fw-semibold text-secondary">Name</label>
                    <input class="form-control" name="name" [(ngModel)]="listForm.name" required
                           placeholder="e.g. Retail price book">
                  </div>
                </div>
                <div class="row g-2">
                  <div class="col-md-4">
                    <label class="form-label small fw-semibold text-secondary">Currency</label>
                    <orbix-search-select name="ccy" [options]="currencyOptions()"
                                         [(ngModel)]="listForm.currencyCode" placeholder="Select…" required>
                    </orbix-search-select>
                  </div>
                  <div class="col-md-4">
                    <label class="form-label small fw-semibold text-secondary">Valid from</label>
                    <input class="form-control" type="date" name="vf" [(ngModel)]="listForm.validFrom" required>
                  </div>
                  <div class="col-md-4">
                    <label class="form-label small fw-semibold text-secondary">Valid to <span class="text-muted">(optional)</span></label>
                    <input class="form-control" type="date" name="vt" [(ngModel)]="listForm.validTo">
                  </div>
                </div>
                <div class="d-flex gap-3 flex-wrap">
                  <div class="form-check">
                    <input class="form-check-input" type="checkbox" id="ti" name="ti"
                           [(ngModel)]="listForm.taxInclusive">
                    <label class="form-check-label small" for="ti">Tax inclusive</label>
                  </div>
                  <div class="form-check">
                    <input class="form-check-input" type="checkbox" id="def" name="def"
                           [(ngModel)]="listForm.isDefault">
                    <label class="form-check-label small" for="def">Use as company default</label>
                  </div>
                </div>
                <div class="d-flex gap-2 pt-2 border-top">
                  <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                          [disabled]="busy() || lf.invalid">
                    @if (busy()) {
                      <span class="spinner-border spinner-border-sm"></span>
                    } @else {
                      <i class="bi" [class.bi-save]="mode() === 'edit-list'" [class.bi-plus-lg]="mode() === 'create'"></i>
                    }
                    {{ mode() === 'edit-list' ? 'Save changes' : 'Create price list' }}
                  </button>
                  <button type="button" class="btn btn-outline-secondary" (click)="cancelListEdit()">Cancel</button>
                </div>
              </form>
            </div>
          </div>
        } @else {
          @if (selected(); as list) {
          <!-- Selected price-list detail -->
          <div class="card border-0 shadow-sm mb-3">
            <div class="card-body p-4 d-flex flex-wrap align-items-start justify-content-between gap-3">
              <div>
                <p class="small text-secondary mb-1">
                  <span class="font-monospace">{{ list.code }}</span> · {{ list.currencyCode }}
                  @if (list.taxInclusive) { · tax-inclusive }
                </p>
                <h2 class="h5 fw-bold mb-1 text-dark">{{ list.name }}</h2>
                <p class="small text-secondary mb-0">
                  Valid {{ list.validFrom | date:'mediumDate' }}
                  @if (list.validTo) { → {{ list.validTo | date:'mediumDate' }} }
                  @else { onwards }
                </p>
              </div>
              <div class="d-flex gap-2">
                <button class="btn btn-sm btn-outline-secondary d-inline-flex align-items-center gap-1"
                        (click)="editList(list)" [disabled]="busy()">
                  <i class="bi bi-pencil"></i> Edit
                </button>
                @if (list.status === 'ACTIVE') {
                  <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1"
                          (click)="archiveList(list)" [disabled]="busy()">
                    <i class="bi bi-archive"></i> Archive
                  </button>
                } @else {
                  <button class="btn btn-sm btn-success d-inline-flex align-items-center gap-1"
                          (click)="activateList(list)" [disabled]="busy()">
                    <i class="bi bi-arrow-counterclockwise"></i> Restore
                  </button>
                }
              </div>
            </div>
          </div>

          @if (list.status === 'ACTIVE') {
          <!-- Set price form -->
          <div class="card border-0 shadow-sm mb-3">
            <div class="card-header bg-white border-bottom p-3">
              <h3 class="h6 fw-bold mb-0 text-dark">Set a price</h3>
              <p class="small text-secondary mb-0">Closes the item's current price the day before the effective date and opens a new one.</p>
            </div>
            <div class="card-body p-3">
              <form (ngSubmit)="submitPrice(list)" #pf="ngForm" class="row g-2 align-items-end">
                <div class="col-12 col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Item</label>
                  <orbix-search-select name="iid" [options]="itemOptions()"
                                       [(ngModel)]="priceForm.itemId" placeholder="Select an item…" required>
                  </orbix-search-select>
                </div>
                <div class="col-6 col-md-3">
                  <label class="form-label small fw-semibold text-secondary">UoM</label>
                  <orbix-search-select name="uid" [options]="uomOptions()"
                                       [(ngModel)]="priceForm.uomId" placeholder="Select…" required>
                  </orbix-search-select>
                </div>
                <div class="col-6 col-md-2">
                  <label class="form-label small fw-semibold text-secondary">Min qty <span class="text-muted">(tier)</span></label>
                  <input class="form-control text-end" type="number" step="0.0001" min="0" name="mq"
                         [(ngModel)]="priceForm.minQty" placeholder="0">
                </div>
                <div class="col-6 col-md-3">
                  <label class="form-label small fw-semibold text-secondary">Price</label>
                  <input class="form-control text-end" type="number" step="0.0001" name="prc"
                         [(ngModel)]="priceForm.price" required>
                </div>
                <div class="col-6 col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Effective from</label>
                  <input class="form-control" type="date" name="eff"
                         [(ngModel)]="priceForm.effectiveFrom" required>
                </div>
                <div class="col-12 col-md-5">
                  <label class="form-label small fw-semibold text-secondary">Reason <span class="text-muted">(optional)</span></label>
                  <input class="form-control" name="rsn" [(ngModel)]="priceForm.reason" placeholder="e.g. cost rise">
                </div>
                <div class="col-6 col-md-3">
                  <label class="form-label small fw-semibold text-secondary">Authoriser ID <span class="text-muted">(if needed)</span></label>
                  <input class="form-control" type="number" name="apr" [(ngModel)]="priceForm.approverId"
                         placeholder="for large changes">
                </div>
                <div class="col-12">
                  <button class="btn btn-primary d-inline-flex align-items-center gap-2"
                          [disabled]="busy() || pf.invalid">
                    <i class="bi bi-cash-coin"></i> Set price
                  </button>
                </div>
              </form>
            </div>
          </div>

          <!-- Bulk operations -->
          <div class="card border-0 shadow-sm mb-3">
            <button type="button"
                    class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between w-100 border-0 text-start"
                    (click)="showBulk.set(!showBulk())">
              <h3 class="h6 fw-bold mb-0 text-dark"><i class="bi bi-stack me-2"></i>Bulk price changes</h3>
              <i class="bi" [class.bi-chevron-down]="!showBulk()" [class.bi-chevron-up]="showBulk()"></i>
            </button>
            @if (showBulk()) {
              <div class="card-body p-3 d-flex flex-column gap-4">
                <!-- Adjust all -->
                <form (ngSubmit)="submitAdjust(list)" #af="ngForm" class="row g-2 align-items-end">
                  <div class="col-12"><span class="small fw-semibold text-dark">Shift every current price by a percentage</span></div>
                  <div class="col-6 col-md-2">
                    <label class="form-label small fw-semibold text-secondary">Adjust %</label>
                    <input class="form-control text-end" type="number" step="0.01" name="apct"
                           [(ngModel)]="adjustForm.adjustPct" required placeholder="e.g. 5 or -10">
                  </div>
                  <div class="col-6 col-md-3">
                    <label class="form-label small fw-semibold text-secondary">Effective from</label>
                    <input class="form-control" type="date" name="aeff" [(ngModel)]="adjustForm.effectiveFrom" required>
                  </div>
                  <div class="col-12 col-md-4">
                    <label class="form-label small fw-semibold text-secondary">Reason <span class="text-muted">(optional)</span></label>
                    <input class="form-control" name="arsn" [(ngModel)]="adjustForm.reason" placeholder="e.g. annual uplift">
                  </div>
                  <div class="col-6 col-md-3">
                    <label class="form-label small fw-semibold text-secondary">Authoriser ID <span class="text-muted">(if needed)</span></label>
                    <input class="form-control" type="number" name="aapr" [(ngModel)]="adjustForm.approverId">
                  </div>
                  <div class="col-12">
                    <button class="btn btn-outline-primary btn-sm d-inline-flex align-items-center gap-2"
                            [disabled]="busy() || af.invalid">
                      <i class="bi bi-percent"></i> Apply to all prices
                    </button>
                  </div>
                </form>

                <!-- Copy from another list -->
                <form (ngSubmit)="submitCopy(list)" #cf="ngForm" class="row g-2 align-items-end border-top pt-3">
                  <div class="col-12"><span class="small fw-semibold text-dark">Copy prices from another list</span></div>
                  <div class="col-12 col-md-4">
                    <label class="form-label small fw-semibold text-secondary">Source list</label>
                    <orbix-search-select name="src" [options]="sourceListOptions()"
                                         [(ngModel)]="copyForm.sourcePriceListUid" placeholder="Select a list…" required>
                    </orbix-search-select>
                  </div>
                  <div class="col-6 col-md-2">
                    <label class="form-label small fw-semibold text-secondary">Adjust % <span class="text-muted">(opt)</span></label>
                    <input class="form-control text-end" type="number" step="0.01" name="cpct"
                           [(ngModel)]="copyForm.adjustPct" placeholder="0">
                  </div>
                  <div class="col-6 col-md-3">
                    <label class="form-label small fw-semibold text-secondary">Effective from</label>
                    <input class="form-control" type="date" name="ceff" [(ngModel)]="copyForm.effectiveFrom" required>
                  </div>
                  <div class="col-6 col-md-3">
                    <label class="form-label small fw-semibold text-secondary">Authoriser ID <span class="text-muted">(if needed)</span></label>
                    <input class="form-control" type="number" name="capr" [(ngModel)]="copyForm.approverId">
                  </div>
                  <div class="col-12">
                    <button class="btn btn-outline-primary btn-sm d-inline-flex align-items-center gap-2"
                            [disabled]="busy() || cf.invalid">
                      <i class="bi bi-files"></i> Copy prices here
                    </button>
                    <span class="small text-secondary ms-2">Source must share this list's currency ({{ list.currencyCode }}).</span>
                  </div>
                </form>
              </div>
            }
          </div>
          }

          <!-- Current prices -->
          <div class="card border-0 shadow-sm overflow-hidden">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h3 class="h6 fw-bold mb-0 text-dark">Current prices</h3>
              <span class="badge text-bg-light text-secondary">{{ prices().length }}</span>
            </div>
            @if (prices().length === 0) {
              <div class="p-5 text-center">
                <div class="empty-icon mx-auto mb-3"><i class="bi bi-tag"></i></div>
                <p class="small text-secondary mb-0">No prices set on this list yet.</p>
              </div>
            } @else {
              <div class="table-responsive">
                <table class="table table-hover align-middle mb-0 simple-table">
                  <thead>
                    <tr>
                      <th>Item</th><th>UoM</th><th class="text-end">Tier (min qty)</th>
                      <th class="text-end">Price ({{ list.currencyCode }})</th>
                      <th>Valid from</th>
                      @if (list.status === 'ACTIVE') { <th class="text-end">Actions</th> }
                    </tr>
                  </thead>
                  <tbody>
                    @for (row of prices(); track row.id) {
                      <tr>
                        <td>
                          <span class="fw-semibold text-dark">{{ row.itemCode ?? ('#' + row.itemId) }}</span>
                          @if (row.itemName) { <span class="small text-secondary d-block">{{ row.itemName }}</span> }
                        </td>
                        <td><span class="badge text-bg-light border text-secondary">{{ row.uomCode ?? ('#' + row.uomId) }}</span></td>
                        <td class="text-end small text-secondary">{{ row.minQty > 0 ? (row.minQty | number:'1.0-4') : 'any' }}</td>
                        <td class="text-end fw-semibold text-dark">{{ row.price | number:'1.2-4' }}</td>
                        <td class="small text-secondary">{{ row.validFrom | date:'mediumDate' }}</td>
                        @if (list.status === 'ACTIVE') {
                          <td class="text-end">
                            <button class="btn btn-sm btn-outline-danger" (click)="discontinue(list, row)" [disabled]="busy()"
                                    title="Discontinue this price">
                              <i class="bi bi-x-circle"></i>
                            </button>
                          </td>
                        }
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }

    .pl-row {
      width: 100%;
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.875rem 1rem;
      background: #fff;
      border: none;
      border-bottom: 1px solid #f3f4f6;
      text-align: left;
      transition: background 0.1s ease;
    }
    .pl-row:hover { background: #f8fafc; }
    .pl-row.is-active {
      background: #eef4ff;
      border-left: 3px solid #1d4ed8;
      padding-left: calc(1rem - 3px);
    }
    .pl-row:last-child { border-bottom: none; }
    .min-w-0 { min-width: 0; }

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

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.2rem 0.55rem; border-radius: 999px;
      font-size: 0.7rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 5px; height: 5px; border-radius: 50%; }
    .status-badge--active   { background: #d1fae5; color: #047857; }
    .status-badge--active .status-badge__dot   { background: #10b981; }
    .status-badge--inactive { background: #fef3c7; color: #92400e; }
    .status-badge--inactive .status-badge__dot { background: #f59e0b; }
    .status-badge--archived { background: #f3f4f6; color: #4b5563; }
    .status-badge--archived .status-badge__dot { background: #9ca3af; }

    .text-bg-primary-subtle { background: #e0ecff; color: #1d4ed8; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #ffe4e6; color: #be123c; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8;
      box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }
  `]
})
export class PriceListComponent implements OnInit {
  private readonly catalog = inject(CatalogService);
  private readonly currencyService = inject(CurrencyService);

  protected readonly currencies = signal<Currency[]>([]);
  protected readonly currencyOptions = computed<SearchSelectOption[]>(() =>
    this.currencies()
      .filter(c => c.status === 'ACTIVE')
      .map(c => ({ id: c.code, label: `${c.code} · ${c.name}` }))
  );

  protected readonly items = signal<Item[]>([]);
  protected readonly uoms = signal<Uom[]>([]);
  protected readonly itemOptions = computed<SearchSelectOption[]>(() =>
    this.items().map(i => ({ id: i.id, label: `${i.code} · ${i.name}` })));
  protected readonly uomOptions = computed<SearchSelectOption[]>(() =>
    this.uoms().map(u => ({ id: u.id, label: `${u.code} · ${u.name}` })));
  protected readonly sourceListOptions = computed<SearchSelectOption[]>(() => {
    const current = this.selected();
    return this.priceLists()
      .filter(l => l.uid !== current?.uid && l.currencyCode === current?.currencyCode)
      .map(l => ({ id: l.uid, label: `${l.code} · ${l.name}` }));
  });

  protected readonly priceLists = signal<PriceList[]>([]);
  protected readonly selected = signal<PriceList | null>(null);
  protected readonly prices = signal<PriceListItem[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly showBulk = signal(false);

  protected readonly statusFilter = signal<'ACTIVE' | 'ARCHIVED' | 'ALL'>('ACTIVE');
  protected readonly archivedCount = computed(() =>
    this.priceLists().filter(l => l.status === 'ARCHIVED').length);
  protected readonly filteredPriceLists = computed(() => {
    const sf = this.statusFilter();
    return sf === 'ALL' ? this.priceLists() : this.priceLists().filter(l => l.status === sf);
  });

  protected readonly mode = signal<'view' | 'create' | 'edit-list'>('view');

  protected listForm = blankListForm();
  protected priceForm = blankPriceForm();
  protected adjustForm = blankAdjustForm();
  protected copyForm = blankCopyForm();

  ngOnInit(): void {
    this.loadLists();
    this.currencyService.listCurrencies().subscribe({
      next: list => this.currencies.set(list),
      error: () => this.currencies.set([])
    });
    this.catalog.listItems('ACTIVE', 0, 1000).subscribe({
      next: page => this.items.set(page.content),
      error: () => this.items.set([])
    });
    this.catalog.listUoms().subscribe({
      next: list => this.uoms.set(list.filter(u => u.status === 'ACTIVE')),
      error: () => this.uoms.set([])
    });
  }

  select(list: PriceList): void {
    this.selected.set(list);
    this.mode.set('view');
    this.loadPrices(list.uid);
  }

  startNew(): void {
    this.mode.set('create');
    this.selected.set(null);
    this.listForm = blankListForm();
  }

  editList(list: PriceList): void {
    this.mode.set('edit-list');
    this.listForm = {
      code: list.code,
      name: list.name,
      currencyCode: list.currencyCode,
      validFrom: list.validFrom,
      validTo: list.validTo ?? '',
      isDefault: list.isDefault,
      taxInclusive: list.taxInclusive
    };
  }

  cancelListEdit(): void {
    this.mode.set('view');
    if (!this.selected()) {
      this.listForm = blankListForm();
    }
  }

  submitList(): void {
    const isEdit = this.mode() === 'edit-list';
    const uid = isEdit ? this.selected()?.uid ?? null : null;
    const request = {
      name: this.listForm.name.trim(),
      currencyCode: this.listForm.currencyCode.trim().toUpperCase(),
      validFrom: this.listForm.validFrom,
      validTo: this.listForm.validTo || null,
      isDefault: this.listForm.isDefault,
      taxInclusive: this.listForm.taxInclusive
    };
    const source = uid
      ? this.catalog.updatePriceList(uid, request)
      : this.catalog.createPriceList({ code: this.listForm.code.trim(), ...request });
    this.run(source, list => {
      this.loadLists();
      this.select(list);
    });
  }

  setStatusFilter(value: 'ACTIVE' | 'ARCHIVED' | 'ALL'): void {
    this.statusFilter.set(value);
  }

  archiveList(list: PriceList): void {
    this.run(this.catalog.archivePriceList(list.uid), () => {
      this.selected.set(null);
      this.mode.set('view');
      this.loadLists();
    });
  }

  activateList(list: PriceList): void {
    this.run(this.catalog.activatePriceList(list.uid), () => {
      this.selected.set({ ...list, status: 'ACTIVE' });
      this.loadLists();
    });
  }

  submitPrice(list: PriceList): void {
    if (!this.priceForm.itemId || !this.priceForm.uomId) {
      return;
    }
    this.run(this.catalog.setPrice(list.uid, {
      itemId: this.priceForm.itemId,
      uomId: this.priceForm.uomId,
      minQty: this.priceForm.minQty != null ? Number(this.priceForm.minQty) : null,
      price: Number(this.priceForm.price),
      effectiveFrom: this.priceForm.effectiveFrom,
      reason: this.priceForm.reason.trim() || null,
      approverId: this.priceForm.approverId ? String(this.priceForm.approverId) : null
    }), () => {
      this.priceForm = blankPriceForm();
      this.loadPrices(list.uid);
      this.notice.set('Price set.');
    });
  }

  discontinue(list: PriceList, row: PriceListItem): void {
    if (!confirm(`Discontinue the price for ${row.itemCode ?? row.itemId} (${row.uomCode ?? row.uomId}) from today?`)) {
      return;
    }
    this.run(this.catalog.discontinuePrice(list.uid, {
      itemId: row.itemId,
      uomId: row.uomId,
      minQty: row.minQty,
      effectiveFrom: today(),
      reason: null
    }), () => {
      this.loadPrices(list.uid);
      this.notice.set('Price discontinued.');
    });
  }

  submitAdjust(list: PriceList): void {
    this.run(this.catalog.adjustPrices(list.uid, {
      adjustPct: Number(this.adjustForm.adjustPct),
      effectiveFrom: this.adjustForm.effectiveFrom,
      reason: this.adjustForm.reason.trim() || null,
      approverId: this.adjustForm.approverId ? String(this.adjustForm.approverId) : null
    }), count => {
      this.adjustForm = blankAdjustForm();
      this.loadPrices(list.uid);
      this.notice.set(`Adjusted ${count} price${count === 1 ? '' : 's'}.`);
    });
  }

  submitCopy(list: PriceList): void {
    if (!this.copyForm.sourcePriceListUid) {
      return;
    }
    this.run(this.catalog.copyPrices(list.uid, {
      sourcePriceListUid: this.copyForm.sourcePriceListUid,
      adjustPct: this.copyForm.adjustPct != null ? Number(this.copyForm.adjustPct) : null,
      effectiveFrom: this.copyForm.effectiveFrom,
      reason: null,
      approverId: this.copyForm.approverId ? String(this.copyForm.approverId) : null
    }), count => {
      this.copyForm = blankCopyForm();
      this.loadPrices(list.uid);
      this.notice.set(`Copied ${count} price${count === 1 ? '' : 's'}.`);
    });
  }

  private loadLists(): void {
    this.catalog.listPriceLists().subscribe({
      next: lists => this.priceLists.set(lists),
      error: err => this.showError(err)
    });
  }

  private loadPrices(priceListUid: string): void {
    this.catalog.listPrices(priceListUid).subscribe({
      next: prices => this.prices.set(prices),
      error: err => this.showError(err)
    });
  }

  private run<T>(source: Observable<T>, onSuccess: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    source.subscribe({
      next: value => { this.busy.set(false); onSuccess(value); },
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

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function blankListForm() {
  return {
    code: '', name: '', currencyCode: 'TZS',
    validFrom: today(), validTo: '',
    isDefault: false, taxInclusive: false
  };
}

function blankPriceForm() {
  return {
    itemId: null as string | null,
    uomId: null as string | null,
    minQty: null as number | null,
    price: null as number | null,
    effectiveFrom: today(),
    reason: '',
    approverId: null as number | null
  };
}

function blankAdjustForm() {
  return {
    adjustPct: null as number | null,
    effectiveFrom: today(),
    reason: '',
    approverId: null as number | null
  };
}

function blankCopyForm() {
  return {
    sourcePriceListUid: null as string | null,
    adjustPct: null as number | null,
    effectiveFrom: today(),
    approverId: null as number | null
  };
}

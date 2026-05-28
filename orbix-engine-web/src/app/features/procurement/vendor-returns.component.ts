import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { PagerComponent } from '../../core/ui/pager.component';
import { ProcurementService } from './procurement.service';
import {
  VendorCreditNote,
  VendorCreditNoteStatus,
  VendorReturn,
} from './procurement.models';
import { VendorCreditNoteApplyModalComponent } from './vendor-credit-note-apply-modal.component';

@Component({
  selector: 'orbix-vendor-returns',
  standalone: true,
  imports: [
    CommonModule, RouterLink, DatePipe, DecimalPipe,
    PagerComponent, VendorCreditNoteApplyModalComponent,
  ],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Procurement</a> &rsaquo; Vendor returns
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Vendor returns</h1>
        <p class="text-secondary mb-0 small">Returns to suppliers and credit-note allocation.</p>
      </div>
      <a routerLink="new"
         class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm">
        <i class="bi bi-plus-lg" aria-hidden="true"></i>
        New return
      </a>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2" role="alert">
        <i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss error" (click)="error.set(null)"></button>
      </div>
    }
    @if (info()) {
      <div class="alert alert-success d-flex align-items-center gap-2 py-2" role="status">
        <i class="bi bi-check-circle-fill" aria-hidden="true"></i>
        <span class="flex-grow-1">{{ info() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss message" (click)="info.set(null)"></button>
      </div>
    }

    <!-- Tabs -->
    <ul class="nav nav-tabs mb-3" role="tablist">
      <li class="nav-item" role="presentation">
        <button class="nav-link" [class.active]="activeTab() === 'returns'"
                role="tab" [attr.aria-selected]="activeTab() === 'returns'"
                (click)="switchTab('returns')">
          Returns <span class="badge text-bg-secondary ms-1">{{ total() }}</span>
        </button>
      </li>
      <li class="nav-item" role="presentation">
        <button class="nav-link" [class.active]="activeTab() === 'credit-notes'"
                role="tab" [attr.aria-selected]="activeTab() === 'credit-notes'"
                (click)="switchTab('credit-notes')">
          Credit notes <span class="badge text-bg-secondary ms-1">{{ creditNotes().length }}</span>
        </button>
      </li>
    </ul>

    <!-- Returns tab -->
    @if (activeTab() === 'returns') {
      <div class="row g-3 g-md-4" role="tabpanel" aria-label="Returns">
        <!-- Returns list -->
        <div class="col-12 col-lg-5">
          <div class="card border-0 shadow-sm overflow-hidden">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h2 class="h6 fw-bold mb-0 text-dark">Returns</h2>
              <span class="badge text-bg-light text-secondary">{{ total() }}</span>
            </div>
            @if (returns().length === 0) {
              <div class="p-5 text-center">
                <div class="empty-icon mx-auto mb-3">
                  <i class="bi bi-arrow-return-left" aria-hidden="true"></i>
                </div>
                <p class="small text-secondary mb-0">No vendor returns yet.</p>
              </div>
            } @else {
              <ul class="list-unstyled mb-0 rt-list" role="list">
                @for (r of returns(); track r.id) {
                  <li role="listitem">
                    <button type="button" class="rt-row"
                            [class.is-active]="selected()?.id === r.id"
                            [attr.aria-pressed]="selected()?.id === r.id"
                            (click)="select(r)">
                      <div class="flex-grow-1 min-w-0">
                        <div class="d-flex align-items-center gap-2 mb-1">
                          <span class="badge text-bg-light border text-secondary font-monospace">{{ r.number }}</span>
                          <span class="status-badge status-badge--{{ r.status.toLowerCase() }}"
                                [attr.aria-label]="'Status: ' + r.status">
                            <span class="status-badge__dot" aria-hidden="true"></span>{{ r.status }}
                          </span>
                        </div>
                        <p class="small text-secondary mb-0">
                          Supplier #{{ r.supplierId }} &bull; {{ r.reason }} &bull; {{ r.restock ? 'restock' : 'damage' }}
                        </p>
                      </div>
                      <div class="fw-bold text-dark">{{ r.totalAmount | number:'1.2-2' }}</div>
                    </button>
                  </li>
                }
              </ul>
              @if (totalPages() >= 1) {
                <div class="card-footer bg-white border-top">
                  <orbix-pager [page]="pageNo()" [totalPages]="totalPages()"
                               [totalElements]="total()" [pageSize]="pageSize"
                               (pageChange)="goTo($event)"/>
                </div>
              }
            }
          </div>
        </div>

        <!-- Return detail -->
        <div class="col-12 col-lg-7">
          @if (selected(); as ret) {
            <!-- Return header card -->
            <div class="card border-0 shadow-sm mb-3">
              <div class="card-body p-4">
                <div class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-3">
                  <div>
                    <p class="small text-secondary mb-1">
                      Supplier #{{ ret.supplierId }} &bull; {{ ret.returnDate | date:'mediumDate' }}
                    </p>
                    <h2 class="h4 fw-bold mb-1 text-dark">{{ ret.number }}</h2>
                    <span class="status-badge status-badge--{{ ret.status.toLowerCase() }}"
                          [attr.aria-label]="'Status: ' + ret.status">
                      <span class="status-badge__dot" aria-hidden="true"></span>{{ ret.status }}
                    </span>
                  </div>
                  <div class="d-flex gap-2 flex-wrap">
                    @if (ret.status === 'DRAFT') {
                      @if (canManageReturn()) {
                        <button class="btn btn-sm btn-primary d-inline-flex align-items-center gap-1"
                                [disabled]="busy()" (click)="postReturn(ret)">
                          <i class="bi bi-send" aria-hidden="true"></i> Post
                        </button>
                        <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1"
                                [disabled]="busy()" (click)="cancelReturn(ret)">
                          <i class="bi bi-x-circle" aria-hidden="true"></i> Cancel
                        </button>
                      }
                    }
                    @if (ret.status === 'POSTED') {
                      @if (canManageReturn()) {
                        <button class="btn btn-sm btn-success d-inline-flex align-items-center gap-1"
                                [disabled]="busy()" (click)="issueCreditNote(ret)">
                          <i class="bi bi-receipt" aria-hidden="true"></i> Issue credit note
                        </button>
                      }
                    }
                  </div>
                </div>

                <dl class="row small mb-0">
                  <dt class="col-4 text-secondary">Original GRN</dt>
                  <dd class="col-8 mb-1">{{ ret.originalGrnNumber ?? '—' }}</dd>
                  <dt class="col-4 text-secondary">Reason</dt>
                  <dd class="col-8 mb-1">{{ ret.reason }}</dd>
                  <dt class="col-4 text-secondary">Disposition</dt>
                  <dd class="col-8 mb-1">{{ ret.restock ? 'Restock' : 'Damage discard' }}</dd>
                  <dt class="col-4 text-secondary">Total</dt>
                  <dd class="col-8 mb-1 fw-bold text-dark">{{ ret.totalAmount | number:'1.2-2' }}</dd>
                  @if (ret.notes) {
                    <dt class="col-4 text-secondary">Notes</dt>
                    <dd class="col-8 mb-1">{{ ret.notes }}</dd>
                  }
                </dl>
              </div>
            </div>

            <!-- Lines card -->
            <div class="card border-0 shadow-sm overflow-hidden">
              <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
                <h3 class="h6 fw-bold mb-0 text-dark">Lines</h3>
                <span class="badge text-bg-light text-secondary">{{ ret.lines.length }}</span>
              </div>
              <div class="table-responsive">
                <table class="table table-hover align-middle mb-0 simple-table">
                  <thead>
                    <tr>
                      <th scope="col">#</th>
                      <th scope="col">Item</th>
                      <th scope="col" class="text-end">Qty</th>
                      <th scope="col" class="text-end">Price</th>
                      <th scope="col" class="text-end">Tax</th>
                      <th scope="col" class="text-end">Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (line of ret.lines; track line.id) {
                      <tr>
                        <td class="small text-secondary">{{ line.lineNo }}</td>
                        <td>{{ line.itemName ?? '#' + line.itemId }}</td>
                        <td class="text-end">{{ line.returnedQty }}</td>
                        <td class="text-end">{{ line.unitPrice | number:'1.2-2' }}</td>
                        <td class="text-end small text-secondary">{{ line.taxAmount | number:'1.2-2' }}</td>
                        <td class="text-end fw-semibold">{{ line.lineTotal | number:'1.2-2' }}</td>
                      </tr>
                    } @empty {
                      <tr>
                        <td colspan="6" class="text-center text-secondary py-4">No lines.</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </div>
          } @else {
            <div class="card border-0 shadow-sm">
              <div class="card-body p-5 text-center">
                <div class="empty-icon mx-auto mb-3">
                  <i class="bi bi-cursor" aria-hidden="true"></i>
                </div>
                <h2 class="h6 fw-bold mb-1 text-dark">Pick a return</h2>
                <p class="small text-secondary mb-0">Select a return from the list to view details.</p>
              </div>
            </div>
          }
        </div>
      </div>
    }

    <!-- Credit notes tab -->
    @if (activeTab() === 'credit-notes') {
      <div role="tabpanel" aria-label="Credit notes">
        @if (loadingCreditNotes()) {
          <div class="d-flex align-items-center justify-content-center py-5 gap-2 text-secondary">
            <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
            <span>Loading credit notes…</span>
          </div>
        } @else if (creditNotes().length === 0) {
          <div class="card border-0 shadow-sm">
            <div class="card-body p-5 text-center">
              <div class="empty-icon mx-auto mb-3">
                <i class="bi bi-receipt" aria-hidden="true"></i>
              </div>
              <p class="small text-secondary mb-0">No vendor credit notes yet.</p>
            </div>
          </div>
        } @else {
          <div class="card border-0 shadow-sm overflow-hidden">
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr>
                    <th scope="col">Number</th>
                    <th scope="col">Supplier</th>
                    <th scope="col">Date</th>
                    <th scope="col">Status</th>
                    <th scope="col" class="text-end">Total</th>
                    <th scope="col" class="text-end">Available</th>
                    <th scope="col" class="actions-col">
                      <span class="visually-hidden">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  @for (cn of creditNotes(); track cn.id) {
                    <tr>
                      <td class="font-monospace small">{{ cn.number }}</td>
                      <td class="small text-secondary">#{{ cn.supplierId }}</td>
                      <td class="small text-secondary">{{ cn.cnDate | date:'mediumDate' }}</td>
                      <td>
                        <span class="status-badge {{ cnStatusClass(cn.status) }}"
                              [attr.aria-label]="'Status: ' + cnStatusLabel(cn.status)">
                          <span class="status-badge__dot" aria-hidden="true"></span>
                          {{ cnStatusLabel(cn.status) }}
                        </span>
                      </td>
                      <td class="text-end">{{ cn.totalAmount | number:'1.2-2' }}</td>
                      <td class="text-end fw-semibold">{{ cn.availableAmount | number:'1.2-2' }}</td>
                      <td class="actions-col">
                        @if (cn.status !== 'FULLY_ALLOCATED' && canManageReturn()) {
                          <button class="btn btn-sm btn-outline-primary d-inline-flex align-items-center gap-1"
                                  [attr.aria-label]="'Apply credit note ' + cn.number + ' to a supplier invoice'"
                                  (click)="openApplyModal(cn)">
                            <i class="bi bi-arrow-right-circle" aria-hidden="true"></i> Apply
                          </button>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }
      </div>
    }

    <!-- Apply-credit-note modal -->
    @if (applyModalCreditNote()) {
      <orbix-vendor-credit-note-apply-modal
        [visible]="applyModalOpen()"
        [creditNote]="applyModalCreditNote()!"
        [supplierUid]="applyModalSupplierUid()"
        (creditNoteApplied)="onCreditNoteApplied($event)"
        (closed)="closeApplyModal()"
      />
    }
  `,
  styles: [`
    :host { display: block; }
    .min-w-0 { min-width: 0; }

    .rt-list { max-height: 70vh; overflow-y: auto; }
    .rt-row {
      width: 100%; display: flex; align-items: center; gap: 0.75rem;
      padding: 0.875rem 1rem; background: #fff; border: none;
      border-bottom: 1px solid #f3f4f6; text-align: left;
      transition: background 0.1s ease;
    }
    .rt-row:hover { background: #f8fafc; }
    .rt-row.is-active {
      background: #eef4ff; border-left: 3px solid #1d4ed8;
      padding-left: calc(1rem - 3px);
    }
    .rt-row:last-child { border-bottom: none; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.65rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }
    .simple-table .actions-col { width: 1%; white-space: nowrap; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.25rem 0.625rem; border-radius: 999px;
      font-size: 0.7rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 6px; height: 6px; border-radius: 50%; }

    /* Vendor-return status pills */
    .status-badge--draft    { background: #f3f4f6; color: #4b5563; }
    .status-badge--draft    .status-badge__dot { background: #9ca3af; }
    .status-badge--posted   { background: #e0ecff; color: #1d4ed8; }
    .status-badge--posted   .status-badge__dot { background: #3b82f6; }
    .status-badge--credited { background: #d1fae5; color: #047857; }
    .status-badge--credited .status-badge__dot { background: #10b981; }

    /* Credit-note status pills */
    .cn-posted              { background: #f3f4f6; color: #4b5563; }
    .cn-posted              .status-badge__dot { background: #9ca3af; }
    .cn-partially-allocated { background: #fef9c3; color: #854d0e; }
    .cn-partially-allocated .status-badge__dot { background: #eab308; }
    .cn-fully-allocated     { background: #d1fae5; color: #047857; }
    .cn-fully-allocated     .status-badge__dot { background: #10b981; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #fef3c7; color: #b45309; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class VendorReturnsComponent implements OnInit {
  private readonly procurement = inject(ProcurementService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);

  protected readonly activeTab = signal<'returns' | 'credit-notes'>('returns');

  protected readonly returns = signal<VendorReturn[]>([]);
  protected readonly pageNo = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected readonly pageSize = 20;
  protected readonly selected = signal<VendorReturn | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);

  protected readonly creditNotes = signal<VendorCreditNote[]>([]);
  protected readonly loadingCreditNotes = signal(false);

  protected readonly applyModalOpen = signal(false);
  protected readonly applyModalCreditNote = signal<VendorCreditNote | null>(null);
  protected readonly applyModalSupplierUid = signal('');

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  protected readonly canManageReturn = computed(() =>
    this.auth.hasPermission('PROCUREMENT.MANAGE_RETURN')
  );

  ngOnInit(): void {
    this.refreshReturns();
    this.refreshCreditNotes();
  }

  switchTab(tab: 'returns' | 'credit-notes'): void {
    this.activeTab.set(tab);
    if (tab === 'credit-notes') this.refreshCreditNotes();
  }

  goTo(p: number): void {
    if (p < 0 || p >= this.totalPages()) return;
    this.pageNo.set(p);
    this.refreshReturns();
  }

  select(r: VendorReturn): void { this.selected.set(r); }

  postReturn(r: VendorReturn): void {
    this.runReturn(this.procurement.postVendorReturn(r.uid), `Return ${r.number} posted.`);
  }

  cancelReturn(r: VendorReturn): void {
    if (!globalThis.confirm(`Cancel ${r.number}?`)) return;
    this.runReturn(this.procurement.cancelVendorReturn(r.uid), `Return ${r.number} cancelled.`);
  }

  issueCreditNote(r: VendorReturn): void {
    const cnDate = new Date().toISOString().slice(0, 10);
    const notes = globalThis.prompt(`Notes for credit note against ${r.number} (optional):`);
    if (notes === null) return; // user pressed Cancel
    this.busy.set(true);
    this.error.set(null);
    this.procurement.issueVendorCreditNote(r.uid, { cnDate, notes: notes.trim() || undefined }).subscribe({
      next: cn => {
        this.busy.set(false);
        this.info.set(`Credit note ${cn.number} issued.`);
        this.refreshReturns();
        this.refreshCreditNotes();
      },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  openApplyModal(cn: VendorCreditNote): void {
    const uid = cn.supplierUid ?? cn.supplierId;
    this.applyModalCreditNote.set(cn);
    this.applyModalSupplierUid.set(uid);
    this.applyModalOpen.set(true);
  }

  closeApplyModal(): void {
    this.applyModalOpen.set(false);
    this.applyModalCreditNote.set(null);
  }

  onCreditNoteApplied(updated: VendorCreditNote): void {
    this.creditNotes.update(list =>
      list.map(cn => cn.uid === updated.uid ? updated : cn)
    );
    this.info.set(
      updated.status === 'FULLY_ALLOCATED'
        ? `Credit note ${updated.number} fully allocated.`
        : `Credit note ${updated.number} partially applied — ${updated.availableAmount.toFixed(2)} remaining.`
    );
  }

  cnStatusClass(status: VendorCreditNoteStatus): string {
    switch (status) {
      case 'POSTED':              return 'cn-posted';
      case 'PARTIALLY_ALLOCATED': return 'cn-partially-allocated';
      case 'FULLY_ALLOCATED':     return 'cn-fully-allocated';
      default:                    return 'cn-posted';
    }
  }

  cnStatusLabel(status: VendorCreditNoteStatus): string {
    switch (status) {
      case 'POSTED':              return 'Posted';
      case 'PARTIALLY_ALLOCATED': return 'Partial';
      case 'FULLY_ALLOCATED':     return 'Allocated';
      default:                    return status;
    }
  }

  // ---- private helpers -------------------------------------------------------

  private refreshReturns(): void {
    this.procurement.listVendorReturns(this.branchId(), this.pageNo(), this.pageSize).subscribe({
      next: page => {
        this.returns.set(page.content);
        this.total.set(page.totalElements);
        this.totalPages.set(page.totalPages);
        this.pageNo.set(page.page);
      },
      error: err => this.showError(err)
    });
  }

  private refreshCreditNotes(): void {
    this.loadingCreditNotes.set(true);
    this.procurement.listVendorCreditNotes(this.branchId()).subscribe({
      next: cns => {
        this.creditNotes.set(cns);
        this.loadingCreditNotes.set(false);
      },
      error: () => { this.loadingCreditNotes.set(false); }
    });
  }

  private runReturn(op: Observable<VendorReturn>, msg: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    op.subscribe({
      next: r => {
        this.busy.set(false);
        this.info.set(msg);
        this.selected.set(r);
        this.refreshReturns();
      },
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

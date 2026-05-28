import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { DebtService } from './debt.service';
import { DebtWriteOff, DebtWriteOffStatus, DebtWriteOffTargetKind, RejectDebtWriteOffRequest } from './debt.models';
import { Page, emptyPage } from '../../core/api/page';

/**
 * Slice G.2 — /debt/write-offs queue page (US-DEBT-004).
 *
 * Shows all write-off requests for the company, filterable by status and kind.
 * PENDING_APPROVAL rows display Approve + Reject actions for users with
 * {@code DEBT.WRITE_OFF.APPROVE}.  Clicking a row expands a detail panel
 * with the full audit trail.
 *
 * Four states: loading skeleton / empty / error / populated.
 * Permission-gated at route level by {@code DEBT.READ}; renders a 403 panel
 * on access denial (mirrors the existing debt queue pattern).
 */
@Component({
  selector: 'orbix-debt-write-offs',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe],
  template: `
    <!-- Page header -->
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink="/debt" class="text-decoration-none text-secondary">Debt</a> &rsaquo; Write-offs
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Write-off requests</h1>
        <p class="text-secondary mb-0 small">
          AR and AP invoices submitted for write-off, with approval workflow.
        </p>
      </div>
      <a routerLink="/debt" class="btn btn-outline-secondary d-inline-flex align-items-center gap-2">
        <i class="bi bi-arrow-left"></i> Back to queue
      </a>
    </header>

    <!-- 403 / permission denied -->
    @if (permissionDenied()) {
      <div data-testid="write-offs-permission-required"
           class="alert alert-warning d-flex align-items-start gap-2">
        <i class="bi bi-shield-lock mt-1" aria-hidden="true"></i>
        <div class="flex-grow-1">
          <strong>Permission required.</strong>
          <span class="small d-block text-secondary">
            You don't have the <code>DEBT.READ</code> permission needed to view write-off requests.
          </span>
        </div>
      </div>
    } @else {

      <!-- Filter controls -->
      <div class="d-flex flex-wrap align-items-center gap-3 mb-3">
        <!-- Status chips -->
        <div class="d-flex align-items-center gap-1" role="group" aria-label="Filter by status">
          <span class="small fw-semibold text-secondary me-1">Status:</span>
          @for (s of statusChips; track s.key) {
            <button type="button"
                    class="btn btn-sm"
                    [class.btn-primary]="statusFilter() === s.key"
                    [class.btn-outline-secondary]="statusFilter() !== s.key"
                    [attr.aria-pressed]="statusFilter() === s.key"
                    (click)="setStatusFilter(s.key)">
              {{ s.label }}
            </button>
          }
        </div>
        <!-- Kind chips -->
        <div class="d-flex align-items-center gap-1" role="group" aria-label="Filter by type">
          <span class="small fw-semibold text-secondary me-1">Type:</span>
          @for (k of kindChips; track k.key) {
            <button type="button"
                    class="btn btn-sm"
                    [class.btn-info]="kindFilter() === k.key"
                    [class.btn-outline-secondary]="kindFilter() !== k.key"
                    [attr.aria-pressed]="kindFilter() === k.key"
                    (click)="setKindFilter(k.key)">
              {{ k.label }}
            </button>
          }
        </div>
        <!-- Clear -->
        @if (statusFilter() !== null || kindFilter() !== null) {
          <button type="button"
                  class="btn btn-sm btn-link text-decoration-none"
                  (click)="clearFilters()">
            <i class="bi bi-x-lg" aria-hidden="true"></i> Clear filters
          </button>
        }
      </div>

      <!-- Error banner -->
      @if (error()) {
        <div data-testid="write-offs-error"
             class="alert alert-danger d-flex align-items-center gap-2 py-2 mb-3"
             role="alert">
          <i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>
          <span class="flex-grow-1">{{ error() }}</span>
          <button type="button" class="btn-close btn-sm"
                  aria-label="Dismiss error"
                  (click)="error.set(null)"></button>
        </div>
      }

      <!-- Reject reason modal -->
      @if (rejectingUid()) {
        <div class="modal-backdrop fade show" aria-hidden="true"></div>
        <div class="modal d-block"
             role="dialog"
             aria-modal="true"
             aria-labelledby="rejectModalTitle"
             (keydown.escape)="cancelReject()">
          <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content shadow-lg">
              <div class="modal-header">
                <h2 class="modal-title h5 fw-bold mb-0" id="rejectModalTitle">Reject write-off</h2>
                <button type="button" class="btn-close"
                        aria-label="Close reject dialog"
                        (click)="cancelReject()"></button>
              </div>
              <div class="modal-body">
                <div class="mb-3">
                  <label for="rejectReason" class="form-label fw-semibold">
                    Reason for rejection
                    <span class="text-danger" aria-hidden="true">*</span>
                  </label>
                  <textarea id="rejectReason"
                            class="form-control"
                            [class.is-invalid]="rejectReasonError()"
                            rows="4"
                            maxlength="2000"
                            placeholder="Explain why you are rejecting this write-off request…"
                            aria-describedby="rejectReasonCount"
                            [(ngModel)]="rejectReasonDraft"
                            name="rejectReason"
                            required></textarea>
                  <p id="rejectReasonCount" class="form-text">
                    {{ rejectReasonDraft.length }} / 2000
                  </p>
                  @if (rejectReasonError()) {
                    <div class="invalid-feedback d-block" role="alert">{{ rejectReasonError() }}</div>
                  }
                </div>
              </div>
              <div class="modal-footer">
                <button type="button" class="btn btn-outline-secondary"
                        (click)="cancelReject()">Cancel</button>
                <button type="button" class="btn btn-danger"
                        [disabled]="actionInProgress()"
                        (click)="submitReject()">
                  @if (actionInProgress()) {
                    <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
                  }
                  Reject
                </button>
              </div>
            </div>
          </div>
        </div>
      }

      @if (loading()) {
        <!-- Loading skeleton -->
        <div data-testid="write-offs-loading" class="card border-0 shadow-sm">
          <div class="card-body p-4 text-center text-secondary small">
            <span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>
            Loading write-off requests…
          </div>
        </div>
      } @else if (!loading() && rows().length === 0) {
        <div data-testid="write-offs-empty" class="card border-0 shadow-sm">
          <div class="card-body p-5 text-center text-secondary">
            <i class="bi bi-check2-all fs-2 mb-2 d-block" aria-hidden="true"></i>
            <p class="mb-1 fw-semibold">No write-off requests found</p>
            <p class="small mb-0">
              @if (statusFilter() || kindFilter()) {
                No requests match the current filters. Try clearing them.
              } @else {
                Write-off requests submitted from the customer or supplier drill-downs will appear here.
              }
            </p>
          </div>
        </div>
      } @else if (!loading()) {
        <!-- Populated table -->
        <div class="card border-0 shadow-sm">
          <div class="card-body p-0">
            <div class="table-responsive">
              <table data-testid="write-offs-table"
                     class="table table-hover align-middle mb-0"
                     aria-label="Write-off requests">
                <caption class="visually-hidden">Write-off requests — click a row to view the audit trail</caption>
                <thead>
                  <tr>
                    <th scope="col">Status</th>
                    <th scope="col">Type</th>
                    <th scope="col">Party</th>
                    <th scope="col">Invoice</th>
                    <th scope="col" class="text-end">Amount</th>
                    <th scope="col">Requested by</th>
                    <th scope="col">Requested at</th>
                    <th scope="col">Decided by</th>
                    <th scope="col">Decided at</th>
                    <th scope="col">Reason</th>
                    @if (canApprove()) {
                      <th scope="col" class="text-center">Actions</th>
                    }
                  </tr>
                </thead>
                <tbody>
                  @for (row of rows(); track row.uid) {
                    <!-- Main row -->
                    <tr data-testid="write-off-row"
                        (click)="toggleExpand(row.uid)"
                        (keydown.enter)="toggleExpand(row.uid)"
                        tabindex="0"
                        role="button"
                        [attr.aria-expanded]="expandedUid() === row.uid"
                        [attr.aria-label]="'Toggle details for write-off ' + (row.targetInvoiceNumber ?? row.uid)"
                        style="cursor:pointer;">
                      <td>
                        <span class="badge"
                              [class.text-bg-warning]="row.status === 'PENDING_APPROVAL'"
                              [class.text-bg-success]="row.status === 'POSTED'"
                              [class.text-bg-secondary]="row.status === 'REJECTED'">
                          {{ statusLabel(row.status) }}
                        </span>
                      </td>
                      <td>
                        <span class="badge"
                              [class.text-bg-info]="row.targetKind === 'CUSTOMER_INVOICE'"
                              [class.text-bg-primary]="row.targetKind === 'SUPPLIER_INVOICE'">
                          {{ row.targetKind === 'CUSTOMER_INVOICE' ? 'AR' : 'AP' }}
                        </span>
                      </td>
                      <td class="text-truncate" style="max-width:12rem;">
                        {{ row.partyName ?? '—' }}
                      </td>
                      <td class="font-monospace">
                        @if (row.targetInvoiceNumber) {
                          <a [routerLink]="invoiceRoute(row)"
                             class="text-decoration-none"
                             (click)="$event.stopPropagation()"
                             [attr.aria-label]="'View invoice ' + row.targetInvoiceNumber">
                            {{ row.targetInvoiceNumber }}
                          </a>
                        } @else {
                          <span class="text-secondary small">{{ row.targetInvoiceUid }}</span>
                        }
                      </td>
                      <td class="text-end font-monospace fw-semibold">
                        {{ row.currencyCode }} {{ row.amount | number:'1.0-2' }}
                      </td>
                      <td class="small text-secondary">
                        {{ row.requestedByUsername ?? row.requestedByUserId }}
                      </td>
                      <td class="small text-secondary text-nowrap">
                        {{ row.requestedAt | date:'mediumDate' }}
                      </td>
                      <td class="small text-secondary">
                        {{ resolvedByLabel(row) }}
                      </td>
                      <td class="small text-secondary text-nowrap">
                        {{ resolvedAtLabel(row) | date:'mediumDate' }}
                      </td>
                      <td class="small text-secondary" style="max-width:14rem;">
                        <span class="text-truncate d-inline-block" style="max-width:13rem;"
                              [title]="row.reason">
                          {{ row.reason | slice:0:60 }}{{ row.reason.length > 60 ? '…' : '' }}
                        </span>
                      </td>
                      @if (canApprove()) {
                        <td class="text-center" (click)="$event.stopPropagation()">
                          @if (row.status === 'PENDING_APPROVAL') {
                            <div class="d-flex gap-1 justify-content-center">
                              <button type="button"
                                      class="btn btn-sm btn-outline-success"
                                      [disabled]="actionInProgress()"
                                      [attr.aria-label]="'Approve write-off for ' + (row.targetInvoiceNumber ?? row.uid)"
                                      (click)="approveRow(row.uid)">
                                @if (actionUid() === row.uid && actionType() === 'approve') {
                                  <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
                                } @else {
                                  <i class="bi bi-check-lg" aria-hidden="true"></i>
                                }
                                Approve
                              </button>
                              <button type="button"
                                      class="btn btn-sm btn-outline-danger"
                                      [disabled]="actionInProgress()"
                                      [attr.aria-label]="'Reject write-off for ' + (row.targetInvoiceNumber ?? row.uid)"
                                      (click)="openRejectModal(row.uid)">
                                <i class="bi bi-x-lg" aria-hidden="true"></i> Reject
                              </button>
                            </div>
                          }
                        </td>
                      }
                    </tr>

                    <!-- Expanded detail row -->
                    @if (expandedUid() === row.uid) {
                      <tr data-testid="write-off-detail">
                        <td [attr.colspan]="canApprove() ? 11 : 10" class="bg-light p-0">
                          <div class="p-4">
                            <h3 class="h6 fw-bold text-dark mb-3">Audit trail</h3>
                            <dl class="row g-2 mb-0 small">
                              <dt class="col-sm-3 text-secondary">UID</dt>
                              <dd class="col-sm-9 font-monospace">{{ row.uid }}</dd>

                              <dt class="col-sm-3 text-secondary">Requested by</dt>
                              <dd class="col-sm-9">
                                {{ row.requestedByUsername ?? row.requestedByUserId }}
                                &mdash; {{ row.requestedAt | date:'medium' }}
                              </dd>

                              <dt class="col-sm-3 text-secondary">Amount</dt>
                              <dd class="col-sm-9 font-monospace">
                                {{ row.currencyCode }} {{ row.amount | number:'1.0-2' }}
                              </dd>

                              <dt class="col-sm-3 text-secondary">Reason</dt>
                              <dd class="col-sm-9" style="white-space:pre-wrap;">{{ row.reason }}</dd>

                              @if (row.status === 'POSTED') {
                                <dt class="col-sm-3 text-secondary">Approved by</dt>
                                <dd class="col-sm-9">
                                  {{ row.approvedByUsername ?? row.approvedByUserId ?? '—' }}
                                  @if (row.approvedAt) { &mdash; {{ row.approvedAt | date:'medium' }} }
                                </dd>
                                <dt class="col-sm-3 text-secondary">Posted at</dt>
                                <dd class="col-sm-9">{{ (row.postedAt | date:'medium') ?? '—' }}</dd>
                              }

                              @if (row.status === 'REJECTED') {
                                <dt class="col-sm-3 text-secondary">Rejected by</dt>
                                <dd class="col-sm-9">
                                  {{ row.approvedByUsername ?? row.approvedByUserId ?? '—' }}
                                  @if (row.rejectedAt) { &mdash; {{ row.rejectedAt | date:'medium' }} }
                                </dd>
                                <dt class="col-sm-3 text-secondary">Reason for rejection</dt>
                                <dd class="col-sm-9" style="white-space:pre-wrap;">
                                  {{ row.reasonForReject ?? '—' }}
                                </dd>
                              }
                            </dl>
                          </div>
                        </td>
                      </tr>
                    }
                  }
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- Pagination -->
        @if (page().totalPages > 1) {
          <nav class="mt-3 d-flex justify-content-between align-items-center small text-secondary"
               aria-label="Write-offs pagination">
            <span>
              Page {{ page().page + 1 }} of {{ page().totalPages }}
              ({{ page().totalElements }} total)
            </span>
            <div class="btn-group btn-group-sm">
              <button type="button" class="btn btn-outline-secondary"
                      [disabled]="page().page === 0"
                      aria-label="Previous page"
                      (click)="goPage(page().page - 1)">
                <i class="bi bi-chevron-left" aria-hidden="true"></i> Prev
              </button>
              <button type="button" class="btn btn-outline-secondary"
                      [disabled]="page().page >= page().totalPages - 1"
                      aria-label="Next page"
                      (click)="goPage(page().page + 1)">
                Next <i class="bi bi-chevron-right" aria-hidden="true"></i>
              </button>
            </div>
          </nav>
        }
      }

    }
  `,
  styles: [`
    :host { display: block; }
    .modal-backdrop { z-index: 1040; }
    .modal { z-index: 1050; }
    tbody tr[data-testid="write-off-row"]:focus-visible {
      outline: 2px solid #1d4ed8;
      outline-offset: -2px;
    }
  `]
})
export class DebtWriteOffsComponent implements OnInit {
  private readonly debt = inject(DebtService);
  private readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly permissionDenied = signal(false);

  protected readonly page = signal<Page<DebtWriteOff>>(emptyPage(25));
  protected readonly rows = computed(() => this.page().content);

  protected readonly statusFilter = signal<DebtWriteOffStatus | null>(null);
  protected readonly kindFilter = signal<DebtWriteOffTargetKind | null>(null);

  protected readonly expandedUid = signal<string | null>(null);

  // Approve / reject action state
  protected readonly actionUid = signal<string | null>(null);
  protected readonly actionType = signal<'approve' | 'reject' | null>(null);
  protected readonly actionInProgress = computed(() => this.actionUid() !== null);

  // Reject modal
  protected readonly rejectingUid = signal<string | null>(null);
  protected rejectReasonDraft = '';
  protected readonly rejectReasonError = signal<string | null>(null);

  protected readonly canApprove = computed(() =>
    this.auth.hasPermission('DEBT.WRITE_OFF.APPROVE')
  );

  readonly statusChips: { key: DebtWriteOffStatus; label: string }[] = [
    { key: 'PENDING_APPROVAL', label: 'Pending' },
    { key: 'POSTED',           label: 'Posted' },
    { key: 'REJECTED',         label: 'Rejected' },
  ];

  readonly kindChips: { key: DebtWriteOffTargetKind; label: string }[] = [
    { key: 'CUSTOMER_INVOICE', label: 'AR' },
    { key: 'SUPPLIER_INVOICE', label: 'AP' },
  ];

  ngOnInit(): void {
    this.load(0);
  }

  protected setStatusFilter(s: DebtWriteOffStatus): void {
    this.statusFilter.set(this.statusFilter() === s ? null : s);
    this.load(0);
  }

  protected setKindFilter(k: DebtWriteOffTargetKind): void {
    this.kindFilter.set(this.kindFilter() === k ? null : k);
    this.load(0);
  }

  protected clearFilters(): void {
    this.statusFilter.set(null);
    this.kindFilter.set(null);
    this.load(0);
  }

  protected goPage(p: number): void {
    this.load(p);
  }

  protected toggleExpand(uid: string): void {
    this.expandedUid.set(this.expandedUid() === uid ? null : uid);
  }

  protected statusLabel(s: DebtWriteOffStatus): string {
    switch (s) {
      case 'PENDING_APPROVAL': return 'Pending';
      case 'POSTED':           return 'Posted';
      case 'REJECTED':         return 'Rejected';
    }
  }

  protected invoiceRoute(row: DebtWriteOff): string[] {
    return row.targetKind === 'CUSTOMER_INVOICE'
      ? ['/sales/invoices/uid', row.targetInvoiceUid]
      : ['/procurement/supplier-invoices/uid', row.targetInvoiceUid];
  }

  protected resolvedByLabel(row: DebtWriteOff): string {
    if (row.status === 'PENDING_APPROVAL') return '—';
    return row.approvedByUsername ?? row.approvedByUserId ?? '—';
  }

  protected resolvedAtLabel(row: DebtWriteOff): string | null {
    if (row.status === 'POSTED') return row.postedAt;
    if (row.status === 'REJECTED') return row.rejectedAt;
    return null;
  }

  protected approveRow(uid: string): void {
    this.actionUid.set(uid);
    this.actionType.set('approve');
    this.debt.approveWriteOff(uid).subscribe({
      next: updated => {
        this.actionUid.set(null);
        this.actionType.set(null);
        this.replaceRow(updated);
      },
      error: (err: HttpErrorResponse) => {
        this.actionUid.set(null);
        this.actionType.set(null);
        this.error.set(this.extractMessage(err, 'Could not approve the write-off.'));
      },
    });
  }

  protected openRejectModal(uid: string): void {
    this.rejectReasonDraft = '';
    this.rejectReasonError.set(null);
    this.rejectingUid.set(uid);
  }

  protected cancelReject(): void {
    this.rejectingUid.set(null);
  }

  protected submitReject(): void {
    const uid = this.rejectingUid();
    if (!uid) return;

    if (!this.rejectReasonDraft.trim()) {
      this.rejectReasonError.set('Reason for rejection is required.');
      return;
    }

    this.actionUid.set(uid);
    this.actionType.set('reject');
    const req: RejectDebtWriteOffRequest = { reasonForReject: this.rejectReasonDraft.trim() };

    this.debt.rejectWriteOff(uid, req).subscribe({
      next: updated => {
        this.actionUid.set(null);
        this.actionType.set(null);
        this.rejectingUid.set(null);
        this.replaceRow(updated);
      },
      error: (err: HttpErrorResponse) => {
        this.actionUid.set(null);
        this.actionType.set(null);
        this.rejectingUid.set(null);
        this.error.set(this.extractMessage(err, 'Could not reject the write-off.'));
      },
    });
  }

  private load(pageIndex: number): void {
    this.loading.set(true);
    this.error.set(null);
    const st = this.statusFilter() ?? undefined;
    const kd = this.kindFilter() ?? undefined;

    this.debt.listWriteOffs(st, kd, pageIndex, 25).subscribe({
      next: pg => {
        this.page.set(pg);
        this.loading.set(false);
        this.permissionDenied.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 403) {
          this.permissionDenied.set(true);
        } else {
          this.error.set(this.extractMessage(err, 'Failed to load write-off requests.'));
        }
        this.loading.set(false);
      },
    });
  }

  private replaceRow(updated: DebtWriteOff): void {
    this.page.update(pg => ({
      ...pg,
      content: pg.content.map(r => r.uid === updated.uid ? updated : r),
    }));
  }

  private extractMessage(err: HttpErrorResponse, fallback: string): string {
    const body = err.error;
    if (body && typeof body === 'object' && 'message' in body && body.message) {
      return String((body as { message: unknown }).message);
    }
    return fallback;
  }
}

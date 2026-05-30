import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { Currency, CurrencyService } from '../../core/currency/currency.service';
import { HasPermissionDirective } from '../../core/auth/has-permission.directive';
import { SearchSelectComponent, SearchSelectOption } from '../../core/ui/search-select.component';
import { PagerComponent } from '../../core/ui/pager.component';
import { UserPickerComponent, UserSelectedEvent } from '../../core/ui/user-picker.component';
import { CatalogService } from '../catalog/catalog.service';
import { Item, PriceList } from '../catalog/catalog.models';
import { PartyService } from '../party/party.service';
import { Customer, SalesAgent } from '../party/party.models';
import { SalesService } from './sales.service';
import {
  CreateSalesInvoiceLine,
  PAYMENT_TERMS,
  PaymentTerms,
  REPRINT_REASON_LABELS,
  REPRINT_REASONS,
  ReprintReason,
  SalesInvoice
} from './sales.models';

interface LineRow {
  itemId: string | null;
  qty: number | null;
  unitPrice: number | null;
  discountPct: number | null;
}

@Component({
  selector: 'orbix-sales-invoices',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe, SearchSelectComponent, PagerComponent, HasPermissionDirective, UserPickerComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Sales</a> &rsaquo; Invoices
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Sales invoices</h1>
        <p class="text-secondary mb-0 small">{{ total() }} invoice{{ total() === 1 ? '' : 's' }} on file.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleForm()">
        <i class="bi" [class.bi-plus-lg]="!showForm()" [class.bi-x-lg]="showForm()"></i>
        {{ showForm() ? 'Close form' : 'New invoice' }}
      </button>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="error.set(null)"></button>
      </div>
    }
    @if (info()) {
      <div class="alert alert-success d-flex align-items-center gap-2 py-2">
        <i class="bi bi-check-circle-fill"></i>
        <span class="flex-grow-1">{{ info() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="info.set(null)"></button>
      </div>
    }

    @if (showForm()) {
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">Draft invoice</h2>
          <button class="btn-close btn-sm" aria-label="Close" (click)="toggleForm()"></button>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="create()" #f="ngForm" class="d-flex flex-column gap-3">
            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend"><i class="bi bi-receipt text-secondary"></i> Invoice header</legend>
              <div class="row g-2">
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Invoice number</label>
                  <input class="form-control font-monospace" name="num" [(ngModel)]="newNumber" required placeholder="INV0001">
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Customer</label>
                  <orbix-search-select name="cid" [options]="customerOptions()"
                                       [(ngModel)]="newCustomerId" placeholder="Select a customer…" required>
                  </orbix-search-select>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Sales agent <span class="text-muted">(optional)</span></label>
                  <orbix-search-select name="aid" [options]="salesAgentOptions()"
                                       [(ngModel)]="newSalesAgentId" placeholder="—">
                  </orbix-search-select>
                </div>
                <div class="col-md-3">
                  <label class="form-label small fw-semibold text-secondary">Invoice date</label>
                  <input class="form-control" type="date" name="id" [(ngModel)]="newInvoiceDate" required>
                </div>
                <div class="col-md-3">
                  <label class="form-label small fw-semibold text-secondary">Due date</label>
                  <input class="form-control" type="date" name="dd" [(ngModel)]="newDueDate">
                </div>
                <div class="col-md-3">
                  <label class="form-label small fw-semibold text-secondary">Terms</label>
                  <select class="form-select" name="pt" [(ngModel)]="newPaymentTerms" required>
                    @for (t of paymentTerms; track t) { <option [ngValue]="t">{{ t }}</option> }
                  </select>
                </div>
                <div class="col-md-3">
                  <label class="form-label small fw-semibold text-secondary">Currency</label>
                  <orbix-search-select name="cur" [options]="currencyOptions()"
                                       [(ngModel)]="newCurrency" placeholder="Select…" required>
                  </orbix-search-select>
                </div>
                <div class="col-md-4">
                  <label class="form-label small fw-semibold text-secondary">Price list</label>
                  <orbix-search-select name="pl" [options]="priceListOptions()"
                                       [(ngModel)]="newPriceListId" placeholder="Select a price list…" required>
                  </orbix-search-select>
                </div>
                <div class="col-md-4">
                  <orbix-user-picker
                    instanceId="inv-discount-approver"
                    label="Discount approver (if > 10%)"
                    [required]="false"
                    (userSelected)="onDiscountApproverSelected($event)"
                    (userCleared)="newDiscountApproverId = null">
                  </orbix-user-picker>
                </div>
              </div>
            </fieldset>

            <fieldset class="form-fieldset">
              <legend class="form-fieldset__legend d-flex align-items-center justify-content-between">
                <span><i class="bi bi-list-ul text-secondary"></i> Lines</span>
                <button type="button" class="btn btn-sm btn-outline-primary" (click)="addLine()">
                  <i class="bi bi-plus-lg me-1"></i>Add line
                </button>
              </legend>
              <div class="table-responsive">
                <table class="table table-sm align-middle mb-0 line-table">
                  <thead>
                    <tr>
                      <th>Item</th><th class="text-end">Qty</th>
                      <th class="text-end">Unit price</th><th class="text-end">Disc %</th>
                      <th class="actions-col"></th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (row of newLines; track $index) {
                      <tr>
                        <td>
                          <orbix-search-select [name]="'lid' + $index" [options]="itemOptions()"
                                               [(ngModel)]="row.itemId" placeholder="Select an item…" required>
                          </orbix-search-select>
                        </td>
                        <td>
                          <input class="form-control form-control-sm text-end" type="number" step="0.0001" min="0"
                                 [name]="'lq' + $index" [(ngModel)]="row.qty">
                        </td>
                        <td>
                          <input class="form-control form-control-sm text-end" type="number" step="0.0001" min="0"
                                 [name]="'lp' + $index" [(ngModel)]="row.unitPrice">
                        </td>
                        <td>
                          <input class="form-control form-control-sm text-end" type="number" step="0.01" min="0" max="100"
                                 [name]="'ld' + $index" [(ngModel)]="row.discountPct">
                        </td>
                        <td class="actions-col">
                          <button type="button" class="btn btn-sm btn-outline-secondary"
                                  (click)="removeLine($index)" title="Remove line">
                            <i class="bi bi-x-lg"></i>
                          </button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </fieldset>

            <div class="d-flex gap-2 pt-2 border-top">
              <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="busy() || f.invalid">
                @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                @else { <i class="bi bi-file-earmark-text"></i> }
                Create draft
              </button>
              <button type="button" class="btn btn-outline-secondary" (click)="toggleForm()">Cancel</button>
            </div>
          </form>
        </div>
      </div>
    }

    <div class="row g-3 g-md-4">
      <!-- Invoice list -->
      <div class="col-12 col-lg-5">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3 d-flex flex-wrap align-items-center justify-content-between gap-2">
            <h2 class="h6 fw-bold mb-0 text-dark">Invoices</h2>
            <div class="d-flex align-items-center gap-2">
              <label class="form-label small fw-semibold text-secondary mb-0" for="invoiceStatusFilter">Status</label>
              <select id="invoiceStatusFilter"
                      data-testid="status-filter"
                      class="form-select form-select-sm"
                      [(ngModel)]="statusFilter"
                      (ngModelChange)="onStatusChange($event)">
                @for (opt of statusOptions; track opt) {
                  <option [value]="opt">{{ statusLabelFor(opt) }}</option>
                }
              </select>
              <span class="badge text-bg-light text-secondary">{{ total() }}</span>
            </div>
          </div>
          @if (invoices().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-receipt"></i></div>
              <p class="small text-secondary mb-0">No invoices yet. Draft the first one.</p>
            </div>
          } @else {
            <ul class="list-unstyled mb-0 inv-list">
              @for (inv of invoices(); track inv.id) {
                <li>
                  <button type="button" class="inv-row"
                          [class.is-active]="selected()?.id === inv.id"
                          [title]="inv.creditOverride && inv.creditOverrideReason ? ('Credit override: ' + inv.creditOverrideReason) : null"
                          (click)="select(inv)">
                    <div class="flex-grow-1 min-w-0">
                      <div class="d-flex align-items-center gap-2 mb-1 flex-wrap">
                        <span class="badge text-bg-light border text-secondary font-monospace">{{ inv.number }}</span>
                        <span class="status-badge status-badge--{{ inv.status.toLowerCase() }}">
                          <span class="status-badge__dot"></span>{{ statusLabel(inv.status) }}
                        </span>
                        @if (inv.creditOverride) {
                          <span class="badge text-bg-warning-subtle text-warning-emphasis border border-warning-subtle small">
                            <i class="bi bi-shield-exclamation me-1"></i>Credit override
                          </span>
                        }
                        @if (inv.reprintCount > 0) {
                          <span class="badge text-bg-light text-secondary border small"
                                [title]="'Reprinted ' + inv.reprintCount + ' time' + (inv.reprintCount === 1 ? '' : 's')">
                            <i class="bi bi-printer me-1"></i>{{ inv.reprintCount }}
                          </span>
                        }
                      </div>
                      <p class="small text-secondary mb-0">
                        Customer #{{ inv.customerId }} · {{ inv.invoiceDate | date:'mediumDate' }} · {{ inv.paymentTerms }}
                      </p>
                    </div>
                    <div class="text-end">
                      <div class="fw-bold text-dark">{{ inv.totalAmount | number:'1.2-2' }}</div>
                      @if (inv.paidAmount > 0) {
                        <div class="small text-success">paid {{ inv.paidAmount | number:'1.2-2' }}</div>
                      }
                    </div>
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

      <!-- Invoice detail -->
      <div class="col-12 col-lg-7">
        @if (selected(); as inv) {
          <div class="card border-0 shadow-sm mb-3">
            <div class="card-body p-4">
              <div class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-3">
                <div>
                  <p class="small text-secondary mb-1">Customer #{{ inv.customerId }} · {{ inv.invoiceDate | date:'mediumDate' }}</p>
                  <h2 class="h4 fw-bold mb-1 text-dark">{{ inv.number }}</h2>
                  <span class="status-badge status-badge--{{ inv.status.toLowerCase() }}">
                    <span class="status-badge__dot"></span>{{ statusLabel(inv.status) }}
                  </span>
                </div>
                <div class="d-flex gap-2 flex-wrap">
                  @if (inv.status === 'DRAFT') {
                    <button class="btn btn-sm btn-primary d-inline-flex align-items-center gap-1"
                            [disabled]="busy()" (click)="post(inv)">
                      <i class="bi bi-send"></i> Post
                    </button>
                    <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1"
                            [disabled]="busy()" (click)="cancel(inv)">
                      <i class="bi bi-x-circle"></i> Cancel
                    </button>
                  }
                  @if (inv.status === 'POSTED' || inv.status === 'PARTIALLY_PAID' || inv.status === 'PAID') {
                    <button class="btn btn-sm btn-outline-secondary d-inline-flex align-items-center gap-1"
                            *orbixHasPermission="'SALES_INVOICE.REPRINT'"
                            [disabled]="busy()" (click)="openReprint(inv)"
                            title="Reprint this invoice. The reason is recorded for audit and emits an outbox event.">
                      <i class="bi bi-printer"></i> Reprint
                    </button>
                    <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1"
                            [disabled]="busy()" (click)="voidIt(inv)">
                      <i class="bi bi-slash-circle"></i> Void
                    </button>
                  }
                </div>
              </div>

              @if (creditOverrideTarget()?.id === inv.id) {
                <form (ngSubmit)="confirmCreditOverride()" #ovf="ngForm" class="override-form mb-3">
                  <p class="small fw-semibold text-danger mb-2 d-flex align-items-center gap-2">
                    <i class="bi bi-shield-exclamation"></i>
                    Credit-limit gate tripped — supply a reason to override.
                  </p>
                  <label class="form-label small fw-semibold text-secondary"
                         [attr.for]="'inv-override-reason-' + inv.id">
                    Override reason
                    <span class="text-danger" aria-hidden="true">*</span>
                    <span class="visually-hidden">required</span>
                  </label>
                  <textarea [id]="'inv-override-reason-' + inv.id"
                            class="form-control" name="overrideReason" rows="2" maxlength="500"
                            [(ngModel)]="overrideReason" required
                            placeholder="Why is the credit limit being overridden? (recorded in audit)"></textarea>
                  <p class="small text-secondary mb-0 mt-2">
                    <i class="bi bi-info-circle me-1"></i>
                    The reason is persisted on the invoice and emitted on <span class="font-monospace">SalesInvoicePosted.v1</span>.
                  </p>
                  <div class="d-flex gap-2 mt-2">
                    <button type="submit" class="btn btn-sm btn-warning d-inline-flex align-items-center gap-1"
                            [disabled]="busy() || ovf.invalid">
                      @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                      @else { <i class="bi bi-shield-check"></i> }
                      Post with override
                    </button>
                    <button type="button" class="btn btn-sm btn-outline-secondary"
                            [disabled]="busy()" (click)="closeCreditOverride()">Keep draft</button>
                  </div>
                </form>
              }

              @if (reprintTarget()?.id === inv.id) {
                <form (ngSubmit)="confirmReprint()" #rpf="ngForm" class="reprint-form mb-3">
                  <p class="small fw-semibold text-secondary mb-2 d-flex align-items-center gap-2">
                    <i class="bi bi-printer"></i> Reprint invoice — recorded for audit.
                  </p>
                  <div class="row g-2">
                    <div class="col-md-5">
                      <label class="form-label small fw-semibold text-secondary"
                             [attr.for]="'inv-reprint-reason-' + inv.id">
                        Reason
                        <span class="text-danger" aria-hidden="true">*</span>
                        <span class="visually-hidden">required</span>
                      </label>
                      <select [id]="'inv-reprint-reason-' + inv.id"
                              class="form-select" name="reprintReason"
                              [(ngModel)]="reprintReason" required>
                        @for (r of reprintReasons; track r) {
                          <option [ngValue]="r">{{ reprintReasonLabels[r] }}</option>
                        }
                      </select>
                    </div>
                    <div class="col-md-7">
                      <label class="form-label small fw-semibold text-secondary"
                             [attr.for]="'inv-reprint-notes-' + inv.id">
                        Notes <span class="text-muted">(optional)</span>
                      </label>
                      <textarea [id]="'inv-reprint-notes-' + inv.id"
                                class="form-control" name="reprintNotes" rows="2" maxlength="500"
                                [(ngModel)]="reprintNotes"
                                placeholder="Optional context (e.g. customer lost original)"></textarea>
                    </div>
                  </div>
                  <div class="d-flex gap-2 mt-2">
                    <button type="submit" class="btn btn-sm btn-primary d-inline-flex align-items-center gap-1"
                            [disabled]="busy() || rpf.invalid">
                      @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
                      @else { <i class="bi bi-printer"></i> }
                      Log reprint
                    </button>
                    <button type="button" class="btn btn-sm btn-outline-secondary"
                            [disabled]="busy()" (click)="closeReprint()">Cancel</button>
                  </div>
                </form>
              }

              <!-- Totals strip -->
              <div class="row g-2 totals-row mb-3">
                <div class="col-6 col-md-3">
                  <p class="totals-row__label">Subtotal</p>
                  <p class="totals-row__value">{{ inv.subtotalAmount | number:'1.2-2' }}</p>
                </div>
                <div class="col-6 col-md-3">
                  <p class="totals-row__label">Tax</p>
                  <p class="totals-row__value">{{ inv.taxAmount | number:'1.2-2' }}</p>
                </div>
                <div class="col-6 col-md-3">
                  <p class="totals-row__label">Total</p>
                  <p class="totals-row__value totals-row__value--strong">{{ inv.totalAmount | number:'1.2-2' }}</p>
                </div>
                <div class="col-6 col-md-3">
                  <p class="totals-row__label">Paid</p>
                  <p class="totals-row__value text-success">{{ inv.paidAmount | number:'1.2-2' }}</p>
                </div>
              </div>

              <!-- Meta -->
              <dl class="row small mb-0">
                <dt class="col-4 text-secondary">Terms</dt><dd class="col-8 mb-1">{{ inv.paymentTerms }}</dd>
                <dt class="col-4 text-secondary">Due</dt><dd class="col-8 mb-1">{{ inv.dueDate ? (inv.dueDate | date:'mediumDate') : '—' }}</dd>
                <dt class="col-4 text-secondary">Currency</dt><dd class="col-8 mb-1 font-monospace">{{ inv.currencyCode }}</dd>
                @if (inv.postedAt) {
                  <dt class="col-4 text-secondary">Posted</dt>
                  <dd class="col-8 mb-1">by #{{ inv.postedBy }} on {{ inv.postedBusinessDate }}</dd>
                }
                @if (inv.voidedAt) {
                  <dt class="col-4 text-secondary">Voided</dt>
                  <dd class="col-8 mb-1">by #{{ inv.voidedBy }} — {{ inv.voidReason }}</dd>
                }
                @if (inv.cancellationReason) {
                  <dt class="col-4 text-secondary">Cancellation reason</dt>
                  <dd class="col-8 mb-1">{{ inv.cancellationReason }}</dd>
                }
                @if (inv.creditOverride) {
                  <dt class="col-4 text-secondary">Credit override</dt>
                  <dd class="col-8 mb-1">
                    by #{{ inv.creditOverrideBy }} — {{ inv.creditOverrideReason }}
                  </dd>
                }
                @if (inv.reprintCount > 0) {
                  <dt class="col-4 text-secondary">Reprints</dt>
                  <dd class="col-8 mb-1">{{ inv.reprintCount }}</dd>
                }
              </dl>
            </div>
          </div>

          <!-- Lines -->
          <div class="card border-0 shadow-sm overflow-hidden">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h3 class="h6 fw-bold mb-0 text-dark">Lines</h3>
              <span class="badge text-bg-light text-secondary">{{ inv.lines.length }}</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr>
                    <th>#</th><th>Item</th>
                    <th class="text-end">Qty</th><th class="text-end">Price</th>
                    <th class="text-end">Disc %</th><th class="text-end">Tax</th>
                    <th class="text-end">Total</th>
                  </tr>
                </thead>
                <tbody>
                  @for (line of inv.lines; track line.id) {
                    <tr>
                      <td class="small text-secondary">{{ line.lineNo }}</td>
                      <td><span class="badge text-bg-light border text-secondary font-monospace">#{{ line.itemId }}</span></td>
                      <td class="text-end">{{ line.qty }}</td>
                      <td class="text-end">{{ line.unitPrice | number:'1.2-2' }}</td>
                      <td class="text-end small text-secondary">{{ line.discountPct | number:'1.0-2' }}</td>
                      <td class="text-end small text-secondary">{{ line.taxAmount | number:'1.2-2' }}</td>
                      <td class="text-end fw-semibold">{{ line.lineTotal | number:'1.2-2' }}</td>
                    </tr>
                  } @empty {
                    <tr><td colspan="7" class="text-center text-secondary py-4">No lines.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="card border-0 shadow-sm">
            <div class="card-body p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cursor"></i></div>
              <h2 class="h6 fw-bold mb-1 text-dark">Pick an invoice</h2>
              <p class="small text-secondary mb-0">Or draft a new one to get started.</p>
            </div>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .min-w-0 { min-width: 0; }

    .form-fieldset {
      background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 10px; padding: 1rem 1.25rem 1.25rem;
    }
    .form-fieldset__legend {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #374151; padding: 0 0.5rem; width: 100%; margin-bottom: 0.5rem;
    }
    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }

    .line-table thead th {
      font-size: 0.72rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; border-bottom: 1px solid #e5e7eb; padding: 0.5rem 0.5rem;
    }
    .line-table tbody td { padding: 0.4rem 0.5rem; vertical-align: middle; }
    .line-table .actions-col { width: 1%; white-space: nowrap; }

    .inv-list { max-height: 70vh; overflow-y: auto; }
    .inv-row {
      width: 100%; display: flex; align-items: center; gap: 0.75rem;
      padding: 0.875rem 1rem; background: #fff; border: none;
      border-bottom: 1px solid #f3f4f6; text-align: left;
      transition: background 0.1s ease;
    }
    .inv-row:hover { background: #f8fafc; }
    .inv-row.is-active { background: #eef4ff; border-left: 3px solid #1d4ed8; padding-left: calc(1rem - 3px); }
    .inv-row:last-child { border-bottom: none; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.65rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }

    .totals-row .totals-row__label {
      font-size: 0.72rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; margin-bottom: 0.15rem;
    }
    .totals-row .totals-row__value { font-size: 1.05rem; font-weight: 600; color: #111827; margin-bottom: 0; }
    .totals-row .totals-row__value--strong { font-size: 1.4rem; color: #0d2a5b; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.25rem 0.625rem; border-radius: 999px;
      font-size: 0.7rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 6px; height: 6px; border-radius: 50%; }
    .status-badge--draft           { background: #f3f4f6; color: #4b5563; }
    .status-badge--draft .status-badge__dot           { background: #9ca3af; }
    .status-badge--posted          { background: #e0ecff; color: #1d4ed8; }
    .status-badge--posted .status-badge__dot          { background: #3b82f6; }
    .status-badge--partially_paid  { background: #fef3c7; color: #92400e; }
    .status-badge--partially_paid .status-badge__dot  { background: #f59e0b; }
    .status-badge--paid            { background: #d1fae5; color: #047857; }
    .status-badge--paid .status-badge__dot            { background: #10b981; }
    .status-badge--voided,
    .status-badge--cancelled       { background: #fee2e2; color: #b91c1c; }
    .status-badge--voided .status-badge__dot,
    .status-badge--cancelled .status-badge__dot       { background: #f43f5e; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #e0ecff; color: #1d4ed8; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .override-form {
      background: #fffbeb; border: 1px solid #fde68a; border-radius: 10px;
      padding: 0.875rem 1rem;
    }
    .reprint-form {
      background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px;
      padding: 0.875rem 1rem;
    }
  `]
})
export class InvoicesComponent implements OnInit {
  private readonly sales = inject(SalesService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);
  private readonly party = inject(PartyService);
  private readonly catalog = inject(CatalogService);
  private readonly currencyService = inject(CurrencyService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly currencies = signal<Currency[]>([]);
  protected readonly currencyOptions = computed<SearchSelectOption[]>(() =>
    this.currencies()
      .filter(c => c.status === 'ACTIVE')
      .map(c => ({ id: c.code, label: `${c.code} · ${c.name}` }))
  );

  protected readonly paymentTerms = PAYMENT_TERMS;
  protected readonly invoices = signal<SalesInvoice[]>([]);
  protected readonly pageNo = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected readonly pageSize = 20;
  protected readonly selected = signal<SalesInvoice | null>(null);
  protected readonly customers = signal<Customer[]>([]);
  protected readonly salesAgents = signal<SalesAgent[]>([]);
  protected readonly priceLists = signal<PriceList[]>([]);
  protected readonly items = signal<Item[]>([]);

  protected readonly customerOptions = computed<SearchSelectOption[]>(() =>
    this.customers().map(c => ({ id: c.partyId, label: `${c.party.name} (${c.party.code})` }))
  );
  protected readonly salesAgentOptions = computed<SearchSelectOption[]>(() =>
    this.salesAgents().map(a => ({ id: a.partyId, label: `${a.party.name} (${a.agentCode})` }))
  );
  protected readonly priceListOptions = computed<SearchSelectOption[]>(() =>
    this.priceLists().map(p => ({ id: p.id, label: `${p.name} (${p.code} — ${p.currencyCode})` }))
  );
  protected readonly itemOptions = computed<SearchSelectOption[]>(() =>
    this.items().map(i => ({ id: i.id, label: `${i.name} (${i.code})` }))
  );
  protected readonly busy = signal<boolean>(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly showForm = signal(false);

  // Credit-override + reprint inline-form state (mirrors the procurement
  // cancel pattern in lpos.component.ts / grns.component.ts).
  protected readonly creditOverrideTarget = signal<SalesInvoice | null>(null);
  protected overrideReason = '';
  protected readonly reprintTarget = signal<SalesInvoice | null>(null);
  protected reprintReason: ReprintReason = 'DUPLICATE';
  protected reprintNotes = '';
  protected readonly reprintReasons = REPRINT_REASONS;
  protected readonly reprintReasonLabels = REPRINT_REASON_LABELS;
  protected readonly canOverrideCredit = computed(() =>
    this.auth.hasPermission('SALES_INVOICE.OVERRIDE_CREDIT')
  );

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  protected newNumber = '';
  protected newCustomerId: string | null = null;
  protected newSalesAgentId: string | null = null;
  protected newInvoiceDate = new Date().toISOString().slice(0, 10);
  protected newDueDate: string | null = null;
  protected newPaymentTerms: PaymentTerms = 'CASH';
  protected newCurrency = 'TZS';
  protected newPriceListId: string | null = null;
  protected newDiscountApproverId: string | null = null;
  protected newLines: LineRow[] = [{ itemId: null, qty: null, unitPrice: null, discountPct: null }];

  /**
   * Slice F — status bucket dropdown. {@code ALL} is the local sentinel for
   * "no filter" (translated to {@code null} on the wire). {@code OPEN} and
   * {@code OVERDUE} are backend bucket aliases (POSTED+PARTIALLY_PAID with
   * outstanding > 0; OPEN + due_date < today). The remaining values map
   * directly to the raw {@code SalesInvoiceStatus} enum.
   */
  protected readonly statusOptions = [
    'ALL', 'OPEN', 'OVERDUE',
    'DRAFT', 'POSTED', 'PARTIALLY_PAID', 'PAID', 'VOIDED', 'CANCELLED',
  ] as const;
  protected statusFilter: typeof this.statusOptions[number] = 'ALL';
  /** Optional sort override (e.g. {@code outstanding,desc} for the AR drill-through). */
  protected sortFilter: string | null = null;

  ngOnInit(): void {
    this.loadSelectables();
    this.currencyService.listCurrencies().subscribe({
      next: list => this.currencies.set(list),
      error: () => this.currencies.set([])
    });
    // Slice F — query-param-driven status + sort. On every URL change re-read
    // the filters and refresh. The component is the source of truth for the
    // dropdown state; the URL is the initial / deep-link state.
    this.route.queryParamMap.subscribe(params => {
      const status = params.get('status');
      this.statusFilter = (status && this.isKnownStatus(status)) ? status : 'ALL';
      this.sortFilter = params.get('sort');
      this.pageNo.set(0);
      this.refresh();
    });
  }

  protected statusLabelFor(opt: string): string {
    if (opt === 'ALL') return 'All';
    if (opt === 'PARTIALLY_PAID') return 'Part-paid';
    return opt.charAt(0) + opt.slice(1).toLowerCase().replaceAll('_', ' ');
  }

  protected onStatusChange(value: string): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { status: value === 'ALL' ? null : value },
      queryParamsHandling: 'merge',
    });
  }

  private isKnownStatus(s: string): s is typeof this.statusOptions[number] {
    return (this.statusOptions as readonly string[]).includes(s);
  }

  private loadSelectables(): void {
    // Picker source: pull a large active page (a type-ahead picker is the
    // longer-term answer, mirroring the items picker below).
    this.party.listCustomers('', 'ACTIVE', 0, 500).subscribe({
      next: page => this.customers.set(page.content),
      error: err => this.showError(err)
    });
    this.party.listSalesAgents().subscribe({
      next: rows => this.salesAgents.set(rows.filter(a => a.party.status === 'ACTIVE')),
      error: err => this.showError(err)
    });
    this.catalog.listPriceLists().subscribe({
      next: rows => this.priceLists.set(rows.filter(p => p.status === 'ACTIVE')),
      error: err => this.showError(err)
    });
    // 500 covers most catalogs; items list is paginated server-side and a
    // type-ahead picker is the longer-term answer.
    this.catalog.listItems(null, 0, 500).subscribe({
      next: page => this.items.set(page.content.filter(i => i.status === 'ACTIVE')),
      error: err => this.showError(err)
    });
  }

  toggleForm(): void { this.showForm.update(v => !v); }

  onDiscountApproverSelected(evt: UserSelectedEvent): void { this.newDiscountApproverId = evt.id; }

  refresh(): void {
    const status = this.statusFilter === 'ALL' ? null : this.statusFilter;
    this.sales.listInvoices(this.branchId(), this.pageNo(), this.pageSize, status, this.sortFilter).subscribe({
      next: page => {
        this.invoices.set(page.content);
        this.total.set(page.totalElements);
        this.totalPages.set(page.totalPages);
        this.pageNo.set(page.page);
      },
      error: err => this.showError(err)
    });
  }

  goTo(p: number): void {
    if (p < 0 || p >= this.totalPages()) return;
    this.pageNo.set(p);
    this.refresh();
  }

  select(invoice: SalesInvoice): void {
    this.selected.set(invoice);
    if (this.creditOverrideTarget()?.id !== invoice.id) this.closeCreditOverride();
    if (this.reprintTarget()?.id !== invoice.id) this.closeReprint();
  }

  addLine(): void { this.newLines.push({ itemId: null, qty: null, unitPrice: null, discountPct: null }); }

  removeLine(i: number): void {
    this.newLines.splice(i, 1);
    if (this.newLines.length === 0) this.addLine();
  }

  statusLabel(status: string): string {
    return status === 'PARTIALLY_PAID' ? 'PART-PAID' : status;
  }

  create(): void {
    const branchId = this.branchId();
    if (branchId === null || this.newCustomerId === null || this.newPriceListId === null) {
      this.error.set('Branch, customer, and price list are required.');
      return;
    }
    const lines: CreateSalesInvoiceLine[] = this.newLines
      .filter(r => r.itemId !== null && (r.qty ?? 0) > 0 && (r.unitPrice ?? 0) >= 0)
      .map(r => ({
        itemId: r.itemId as string,
        uomId: null,
        qty: r.qty as number,
        unitPrice: r.unitPrice as number,
        discountPct: r.discountPct,
        vatGroupId: null
      }));
    if (lines.length === 0) {
      this.error.set('Add at least one line.');
      return;
    }
    this.run(this.sales.createInvoice({
      number: this.newNumber.trim(),
      branchId,
      customerId: this.newCustomerId,
      salesAgentId: this.newSalesAgentId,
      invoiceDate: this.newInvoiceDate,
      dueDate: this.newDueDate,
      paymentTerms: this.newPaymentTerms,
      currencyCode: this.newCurrency.trim().toUpperCase(),
      priceListId: this.newPriceListId,
      discountApproverId: this.newDiscountApproverId,
      reference: null,
      notes: null,
      lines
    }), `Invoice ${this.newNumber} created.`);
    this.newNumber = '';
    this.newLines = [{ itemId: null, qty: null, unitPrice: null, discountPct: null }];
    this.showForm.set(false);
  }

  /**
   * Try to post the draft. The backend's credit-limit gate may return 400
   * — when it does, surface the override form (if the caller has the perm)
   * or a clear "permission required" alert (otherwise).
   */
  post(inv: SalesInvoice): void {
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    this.sales.postInvoice(inv.uid).subscribe({
      next: posted => {
        this.busy.set(false);
        this.info.set('Invoice posted.');
        this.selected.set(posted);
        this.refresh();
      },
      error: err => {
        this.busy.set(false);
        if (this.isCreditLimitError(err)) {
          if (this.canOverrideCredit()) {
            this.openCreditOverride(inv);
          } else {
            this.error.set(this.creditLimitMessage(err, inv));
          }
        } else {
          this.showError(err);
        }
      }
    });
  }

  openCreditOverride(inv: SalesInvoice): void {
    this.creditOverrideTarget.set(inv);
    this.overrideReason = '';
    this.closeReprint();
  }

  closeCreditOverride(): void {
    this.creditOverrideTarget.set(null);
    this.overrideReason = '';
  }

  confirmCreditOverride(): void {
    const inv = this.creditOverrideTarget();
    if (!inv) return;
    const reason = this.overrideReason.trim();
    if (reason.length === 0) {
      this.error.set('A reason is required to override the credit limit.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    this.sales.postInvoice(inv.uid, reason).subscribe({
      next: posted => {
        this.busy.set(false);
        this.info.set('Invoice posted with credit override — recorded in audit.');
        this.selected.set(posted);
        this.closeCreditOverride();
        this.refresh();
      },
      error: err => {
        this.busy.set(false);
        this.showError(err);
      }
    });
  }

  openReprint(inv: SalesInvoice): void {
    this.reprintTarget.set(inv);
    this.reprintReason = 'DUPLICATE';
    this.reprintNotes = '';
    this.closeCreditOverride();
  }

  closeReprint(): void {
    this.reprintTarget.set(null);
    this.reprintNotes = '';
  }

  confirmReprint(): void {
    const inv = this.reprintTarget();
    if (!inv) return;
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    const notes = this.reprintNotes.trim();
    this.sales.reprintInvoice(inv.uid, this.reprintReason, notes.length > 0 ? notes : null).subscribe({
      next: updated => {
        this.busy.set(false);
        this.info.set(`Reprint recorded (${updated.reprintCount}${this.ordinalSuffix(updated.reprintCount)} reprint).`);
        this.selected.set(updated);
        this.closeReprint();
        this.refresh();
      },
      error: err => {
        this.busy.set(false);
        this.showError(err);
      }
    });
  }

  voidIt(inv: SalesInvoice): void {
    const reason = globalThis.prompt(`Void ${inv.number} — reason?`);
    if (!reason?.trim()) return;
    this.run(this.sales.voidInvoice(inv.uid, { reason: reason.trim() }), `Invoice voided.`);
  }

  cancel(inv: SalesInvoice): void {
    if (!globalThis.confirm(`Cancel ${inv.number}?`)) return;
    this.run(this.sales.cancelInvoice(inv.uid), `Invoice cancelled.`);
  }

  private run(op: Observable<SalesInvoice>, msg: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    op.subscribe({
      next: inv => {
        this.busy.set(false);
        this.info.set(msg);
        this.selected.set(inv);
        this.refresh();
      },
      error: err => {
        this.busy.set(false);
        this.showError(err);
      }
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

  /** Detect the backend's credit-limit-exceeded 400 by its message. */
  private isCreditLimitError(err: unknown): boolean {
    if (!(err instanceof HttpErrorResponse) || err.status !== 400) return false;
    const envelope = err.error as ApiResponse<unknown> | null;
    const msg = envelope?.message ?? '';
    return /credit limit/i.test(msg);
  }

  /** Resolve the customer name + over-limit message for the no-perm alert. */
  private creditLimitMessage(err: unknown, inv: SalesInvoice): string {
    const envelope = err instanceof HttpErrorResponse
      ? err.error as ApiResponse<unknown> | null
      : null;
    const apiMsg = envelope?.message;
    const customer = this.customers().find(c => c.partyId === inv.customerId);
    const customerName = customer?.party.name ?? `Customer #${inv.customerId}`;
    if (apiMsg) {
      return `Cannot post — ${apiMsg} (${customerName}).`;
    }
    return `Cannot post — credit limit exceeded for ${customerName}.`;
  }

  private ordinalSuffix(n: number): string {
    const rem10 = n % 10;
    const rem100 = n % 100;
    if (rem10 === 1 && rem100 !== 11) return 'st';
    if (rem10 === 2 && rem100 !== 12) return 'nd';
    if (rem10 === 3 && rem100 !== 13) return 'rd';
    return 'th';
  }
}

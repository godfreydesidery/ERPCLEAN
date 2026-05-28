import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { DebtService } from './debt.service';
import { CustomerStatement, PartyNote } from './debt.models';

/**
 * Slice G — per-customer debt drill-down (US-DEBT-001).
 *
 * Composes four panels:
 *  1. Customer header — name + credit-limit + perm-gated adjust form.
 *  2. Open invoices table — from {@code customer.openInvoices}.
 *  3. Recent receipts table — from {@code customer.recentReceipts}.
 *  4. Chase-notes activity log + append form (perm-gated on
 *     {@code DEBT.NOTE.CREATE}).
 *
 * Permission-gating mirrors the dunning-queue page: on 403 from the
 * statement read we hide the drill-down and render an inert
 * "Permission required" panel.
 */
@Component({
  selector: 'orbix-debt-customer',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink="/debt" class="text-decoration-none text-secondary">Debt</a> &rsaquo; Customer
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">
          {{ statement()?.customerName ?? 'Customer drill-down' }}
        </h1>
        @if (statement()) {
          <p class="text-secondary mb-0 small">
            {{ statement()!.openInvoiceCount }} open invoice{{ statement()!.openInvoiceCount === 1 ? '' : 's' }},
            {{ statement()!.overdueInvoiceCount }} overdue.
          </p>
        }
      </div>
      <a routerLink="/debt" class="btn btn-outline-secondary d-inline-flex align-items-center gap-2">
        <i class="bi bi-arrow-left"></i> Back to queue
      </a>
    </header>

    @if (permissionDenied()) {
      <div data-testid="debt-permission-required" class="alert alert-warning d-flex align-items-start gap-2">
        <i class="bi bi-shield-lock mt-1"></i>
        <div class="flex-grow-1">
          <strong>Permission required.</strong>
          <span class="small d-block text-secondary">
            You don't have the <code>DEBT.READ</code> permission needed to view customer debt positions.
          </span>
        </div>
      </div>
    } @else if (loading()) {
      <div class="text-center text-secondary small p-4">
        <span class="spinner-border spinner-border-sm me-2"></span> Loading customer position…
      </div>
    } @else if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2">
        <i class="bi bi-exclamation-triangle-fill"></i>
        <span class="flex-grow-1">{{ error() }}</span>
      </div>
    } @else if (statement()) {
      <div data-testid="debt-customer-detail" class="d-flex flex-column gap-4">

        <!-- 1) Header strip — outstanding + credit-limit + adjust -->
        <section class="card border-0 shadow-sm">
          <div class="card-body p-4">
            <div class="row g-4">
              <div class="col-md-4">
                <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.06em;">
                  Total outstanding
                </p>
                <p class="h4 fw-bold mb-0 text-dark">
                  {{ statement()!.currencyCode }} {{ statement()!.totalOutstanding | number:'1.0-2' }}
                </p>
              </div>
              <div class="col-md-4">
                <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.06em;">
                  Credit limit
                </p>
                <div class="d-flex align-items-center gap-2">
                  @if (!editingLimit()) {
                    <span data-testid="debt-credit-limit-display" class="h5 fw-bold mb-0 text-dark font-monospace">
                      {{ statement()!.currencyCode }} {{ statement()!.creditLimit | number:'1.0-2' }}
                    </span>
                    @if (canAdjustCreditLimit()) {
                      <button data-testid="debt-credit-limit-edit"
                              type="button"
                              class="btn btn-sm btn-outline-secondary"
                              (click)="startEditLimit()">
                        <i class="bi bi-pencil"></i> Adjust
                      </button>
                    }
                  } @else {
                    <input data-testid="debt-credit-limit-input"
                           type="number"
                           class="form-control form-control-sm"
                           style="max-width: 12rem;"
                           min="0"
                           step="1"
                           [(ngModel)]="limitDraft"
                           name="limitDraft"
                           aria-label="New credit limit">
                    <button data-testid="debt-credit-limit-save"
                            type="button"
                            class="btn btn-sm btn-primary"
                            [disabled]="savingLimit()"
                            (click)="saveLimit()">
                      <i class="bi bi-check-lg"></i> Save
                    </button>
                    <button type="button" class="btn btn-sm btn-outline-secondary"
                            [disabled]="savingLimit()"
                            (click)="cancelEditLimit()">
                      Cancel
                    </button>
                  }
                </div>
                @if (limitError()) {
                  <p class="small text-danger mt-1 mb-0">{{ limitError() }}</p>
                }
              </div>
              <div class="col-md-4">
                <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.06em;">
                  Utilisation
                </p>
                <p class="h5 fw-bold mb-0 text-dark">
                  {{ utilisationLabel() }}
                </p>
              </div>
            </div>
          </div>
        </section>

        <!-- 2) Open invoices -->
        <section class="card border-0 shadow-sm">
          <div class="card-header bg-white p-3 border-bottom">
            <h2 class="h6 fw-bold mb-0 text-dark">Open invoices</h2>
          </div>
          <div class="card-body p-0">
            @if (statement()!.openInvoices.length === 0) {
              <p class="text-secondary small p-3 mb-0">No open invoices.</p>
            } @else {
              <div class="table-responsive">
                <table class="table table-sm align-middle mb-0">
                  <thead>
                    <tr>
                      <th>Number</th>
                      <th>Invoice date</th>
                      <th>Due date</th>
                      <th class="text-end">Total</th>
                      <th class="text-end">Paid</th>
                      <th class="text-end">Outstanding</th>
                      <th class="text-end">Days overdue</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (inv of statement()!.openInvoices; track inv.invoiceUid) {
                      <tr>
                        <td class="font-monospace">{{ inv.number }}</td>
                        <td>{{ inv.invoiceDate | date:'mediumDate' }}</td>
                        <td>{{ (inv.dueDate | date:'mediumDate') ?? '—' }}</td>
                        <td class="text-end font-monospace">{{ inv.totalAmount | number:'1.0-2' }}</td>
                        <td class="text-end font-monospace text-secondary">{{ inv.paidAmount | number:'1.0-2' }}</td>
                        <td class="text-end font-monospace fw-semibold">{{ inv.outstanding | number:'1.0-2' }}</td>
                        <td class="text-end">
                          @if (inv.daysOverdue != null && inv.daysOverdue > 0) {
                            <span class="badge text-bg-warning">{{ inv.daysOverdue }} d</span>
                          } @else {
                            <span class="text-secondary small">—</span>
                          }
                        </td>
                        <td><span class="badge text-bg-light text-secondary">{{ inv.status }}</span></td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        </section>

        <!-- 3) Recent receipts -->
        <section class="card border-0 shadow-sm">
          <div class="card-header bg-white p-3 border-bottom">
            <h2 class="h6 fw-bold mb-0 text-dark">Recent receipts</h2>
          </div>
          <div class="card-body p-0">
            @if (statement()!.recentReceipts.length === 0) {
              <p class="text-secondary small p-3 mb-0">No recent receipts.</p>
            } @else {
              <div class="table-responsive">
                <table class="table table-sm align-middle mb-0">
                  <thead>
                    <tr>
                      <th>Number</th>
                      <th>Receipt date</th>
                      <th>Currency</th>
                      <th class="text-end">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (r of statement()!.recentReceipts; track r.receiptUid) {
                      <tr>
                        <td class="font-monospace">{{ r.number }}</td>
                        <td>{{ r.receiptDate | date:'mediumDate' }}</td>
                        <td>{{ r.currencyCode }}</td>
                        <td class="text-end font-monospace">{{ r.totalAmount | number:'1.0-2' }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        </section>

        <!-- 4) Chase notes -->
        <section class="card border-0 shadow-sm">
          <div class="card-header bg-white p-3 border-bottom">
            <h2 class="h6 fw-bold mb-0 text-dark">Chase notes</h2>
          </div>
          <div class="card-body p-3 d-flex flex-column gap-3">
            @if (canAddNote()) {
              <div class="d-flex flex-column gap-2">
                <label for="noteBody" class="form-label small fw-semibold text-secondary mb-0">
                  Add a chase note
                </label>
                <textarea data-testid="debt-chase-note-add"
                          id="noteBody"
                          class="form-control"
                          rows="3"
                          maxlength="1000"
                          placeholder="What did you do? Phoned, emailed, agreed to a payment plan…"
                          [(ngModel)]="noteDraft"
                          name="noteDraft"></textarea>
                @if (noteError()) {
                  <p class="small text-danger mb-0">{{ noteError() }}</p>
                }
                <div>
                  <button data-testid="debt-chase-note-save"
                          type="button"
                          class="btn btn-primary btn-sm"
                          [disabled]="savingNote() || !noteDraft.trim()"
                          (click)="saveNote()">
                    <i class="bi bi-plus-lg"></i> Add note
                  </button>
                </div>
              </div>
            }

            <ul data-testid="debt-chase-notes-list" class="list-unstyled mb-0 d-flex flex-column gap-2">
              @for (n of notes(); track n.uid) {
                <li class="border rounded p-3 d-flex flex-column gap-1"
                    [class.opacity-50]="n.status === 'ARCHIVED'">
                  <div class="d-flex justify-content-between align-items-center small text-secondary">
                    <span>
                      <span class="badge text-bg-light me-1">{{ n.kind }}</span>
                      <span>{{ n.createdAt | date:'medium' }}</span>
                      @if (n.status === 'ARCHIVED') {
                        <span class="badge text-bg-secondary ms-1">Archived</span>
                      }
                    </span>
                  </div>
                  <p class="mb-0">{{ n.body }}</p>
                </li>
              } @empty {
                <li class="text-secondary small">No chase notes yet for this customer.</li>
              }
            </ul>
          </div>
        </section>

      </div>
    }
  `,
  styles: [`
    :host { display: block; }
  `]
})
export class DebtCustomerComponent implements OnInit {
  private readonly debt = inject(DebtService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly permissionDenied = signal(false);

  protected readonly statement = signal<CustomerStatement | null>(null);
  protected readonly notes = signal<PartyNote[]>([]);

  // Credit-limit edit form
  protected readonly editingLimit = signal(false);
  protected readonly savingLimit = signal(false);
  protected readonly limitError = signal<string | null>(null);
  protected limitDraft: number | null = null;

  // Chase-note append form
  protected readonly savingNote = signal(false);
  protected readonly noteError = signal<string | null>(null);
  protected noteDraft = '';

  protected readonly canAdjustCreditLimit = computed(() =>
    this.auth.hasPermission('DEBT.CREDIT_LIMIT.UPDATE')
  );
  protected readonly canAddNote = computed(() =>
    this.auth.hasPermission('DEBT.NOTE.CREATE')
  );

  private customerUid = '';

  ngOnInit(): void {
    this.route.paramMap.subscribe(pm => {
      const uid = pm.get('uid');
      if (!uid) return;
      this.customerUid = uid;
      this.load();
    });
  }

  protected utilisationLabel(): string {
    const s = this.statement();
    if (!s) return '—';
    if (s.creditUtilisation == null) return '—';
    return `${(s.creditUtilisation * 100).toFixed(1)} %`;
  }

  protected startEditLimit(): void {
    const s = this.statement();
    if (!s) return;
    this.limitDraft = s.creditLimit;
    this.limitError.set(null);
    this.editingLimit.set(true);
  }

  protected cancelEditLimit(): void {
    this.editingLimit.set(false);
    this.limitError.set(null);
  }

  protected saveLimit(): void {
    if (this.limitDraft == null || isNaN(Number(this.limitDraft)) || Number(this.limitDraft) < 0) {
      this.limitError.set('Enter a non-negative number.');
      return;
    }
    this.limitError.set(null);
    this.savingLimit.set(true);
    this.debt.adjustCreditLimit(this.customerUid, {
      newLimit: String(this.limitDraft),
      reason: null,
    }).subscribe({
      next: updated => {
        this.statement.set(updated);
        this.editingLimit.set(false);
        this.savingLimit.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.limitError.set(this.formatError(err, 'Could not save credit limit.'));
        this.savingLimit.set(false);
      },
    });
  }

  protected saveNote(): void {
    const body = this.noteDraft.trim();
    if (!body) return;
    this.noteError.set(null);
    this.savingNote.set(true);
    this.debt.createNote({
      customerUid: this.customerUid,
      kind: 'AR_CHASE',
      body,
    }).subscribe({
      next: () => {
        this.noteDraft = '';
        this.savingNote.set(false);
        this.reloadNotes();
      },
      error: (err: HttpErrorResponse) => {
        this.noteError.set(this.formatError(err, 'Could not save the note.'));
        this.savingNote.set(false);
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.permissionDenied.set(false);

    forkJoin({
      statement: this.debt.customerStatement(this.customerUid),
      notes: this.debt.listNotes(this.customerUid, { limit: 50 }),
    }).subscribe({
      next: ({ statement, notes }) => {
        this.statement.set(statement);
        this.notes.set(notes);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 403) {
          this.permissionDenied.set(true);
        } else {
          this.error.set(this.formatError(err, 'Failed to load customer position.'));
        }
        this.loading.set(false);
      },
    });
  }

  private reloadNotes(): void {
    this.debt.listNotes(this.customerUid, { limit: 50 }).subscribe({
      next: notes => this.notes.set(notes),
      error: () => { /* preserve existing list */ },
    });
  }

  private formatError(err: HttpErrorResponse, fallback: string): string {
    const body = err.error;
    if (body && typeof body === 'object' && 'message' in body && body.message) {
      return String((body as { message: unknown }).message);
    }
    return fallback;
  }
}

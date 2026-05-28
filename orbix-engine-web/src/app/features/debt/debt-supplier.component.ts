import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { DebtService } from './debt.service';
import { AgingBucket, OpenSupplierInvoiceRow, PartyNote, SupplierStatement } from './debt.models';
import { DebtWriteOffModalComponent } from './debt-write-off-modal.component';

/**
 * Slice G.1 — per-supplier AP debt drill-down (US-DEBT-002).
 *
 * Five panels stacked vertically — mirrors {@link DebtCustomerComponent}
 * minus the credit-limit edit modal:
 *  1. Header — supplier name + read-only payment-terms-days + "View statement" link.
 *  2. Aging row — 5 bucket cells computed inline from openInvoices.
 *  3. Open AP invoices table — sorted by dueDate asc.
 *     G.2: each row has a perm-gated "Write off" button.
 *  4. Recent payments table — last 30 days, max 50.
 *  5. AP chase notes activity log — append form (kind = AP_CHASE) + list.
 *
 * Permission-gating: on 403 from the statement read, renders inert
 * "Permission required" panel. Note append gated by {@code DEBT.NOTE.CREATE};
 * archive button gated by {@code DEBT.NOTE.ARCHIVE}.
 */
@Component({
  selector: 'orbix-debt-supplier',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe, DecimalPipe, DebtWriteOffModalComponent],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink="/debt" class="text-decoration-none text-secondary">Debt</a> &rsaquo; Supplier
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">
          {{ statement()?.supplierName ?? 'Supplier drill-down' }}
        </h1>
        @if (statement()) {
          <p class="text-secondary mb-0 small">
            {{ statement()!.openInvoiceCount }} open AP invoice{{ statement()!.openInvoiceCount === 1 ? '' : 's' }},
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
        <i class="bi bi-shield-lock mt-1" aria-hidden="true"></i>
        <div class="flex-grow-1">
          <strong>Permission required.</strong>
          <span class="small d-block text-secondary">
            You don't have the <code>DEBT.READ</code> permission needed to view supplier debt positions.
          </span>
        </div>
      </div>
    } @else if (loading()) {
      <div class="text-center text-secondary small p-4">
        <span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span> Loading supplier position…
      </div>
    } @else if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2">
        <i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close" aria-label="Dismiss error" (click)="error.set(null)"></button>
      </div>
    } @else if (statement()) {
      <div data-testid="debt-supplier-detail" class="d-flex flex-column gap-4">

        <!-- 1) Header strip — outstanding + payment-terms (read-only) + statement link -->
        <section class="card border-0 shadow-sm" aria-label="Supplier summary">
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
                  Payment terms
                  <span class="ms-1 text-secondary" style="cursor:help;"
                        title="From supplier master — change via /party/suppliers">
                    <i class="bi bi-info-circle" aria-label="Payment terms are read-only here. Change via the supplier master record."></i>
                  </span>
                </p>
                <p data-testid="debt-payment-terms-display" class="h5 fw-bold mb-0 text-dark">
                  {{ statement()!.paymentTermsDays != null
                     ? statement()!.paymentTermsDays + ' days'
                     : '—' }}
                </p>
              </div>
              <div class="col-md-4 d-flex align-items-start">
                <a [href]="statementUrl()"
                   target="_blank"
                   rel="noopener noreferrer"
                   class="btn btn-outline-secondary btn-sm d-inline-flex align-items-center gap-2 mt-4">
                  <i class="bi bi-file-earmark-text" aria-hidden="true"></i> View statement
                </a>
              </div>
            </div>
          </div>
        </section>

        <!-- 2) Aging row — 5 buckets computed from openInvoices -->
        <section class="card border-0 shadow-sm" aria-label="AP aging buckets">
          <div class="card-body p-0">
            <table class="table table-sm mb-0" style="table-layout:fixed;"
                   aria-label="AP aging summary for this supplier">
              <thead>
                <tr>
                  @for (b of agingBuckets(); track b.key) {
                    <th scope="col" class="bucket-cell">
                      <div class="bucket-label">{{ b.label }}</div>
                      <div class="bucket-amount">
                        {{ statement()!.currencyCode }} {{ b.amount | number:'1.0-0' }}
                      </div>
                    </th>
                  }
                </tr>
              </thead>
            </table>
          </div>
        </section>

        <!-- 3) Open AP invoices -->
        <section class="card border-0 shadow-sm" aria-label="Open AP invoices">
          <div class="card-header bg-white p-3 border-bottom">
            <h2 class="h6 fw-bold mb-0 text-dark">Open AP invoices</h2>
          </div>
          <div class="card-body p-0">
            @if (statement()!.openInvoices.length === 0) {
              <p class="text-secondary small p-3 mb-0">No open AP invoices.</p>
            } @else {
              <div class="table-responsive">
                <table class="table table-sm align-middle mb-0" aria-label="Open AP invoices">
                  <thead>
                    <tr>
                      <th scope="col">Number</th>
                      <th scope="col">Supplier inv. no.</th>
                      <th scope="col">Invoice date</th>
                      <th scope="col">Due date</th>
                      <th scope="col" class="text-end">Total</th>
                      <th scope="col" class="text-end">Paid</th>
                      <th scope="col" class="text-end">Outstanding</th>
                      <th scope="col" class="text-end">Days overdue</th>
                      <th scope="col">Status</th>
                      @if (canRequestWriteOff()) {
                        <th scope="col" class="text-center">Actions</th>
                      }
                    </tr>
                  </thead>
                  <tbody>
                    @for (inv of statement()!.openInvoices; track inv.invoiceUid) {
                      <tr>
                        <td class="font-monospace">
                          <a [routerLink]="['/procurement/supplier-invoices/uid', inv.invoiceUid]"
                             class="text-decoration-none">{{ inv.number }}</a>
                        </td>
                        <td class="text-secondary small">{{ inv.supplierInvoiceNo ?? '—' }}</td>
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
                        @if (canRequestWriteOff()) {
                          <td class="text-center">
                            <button type="button"
                                    data-testid="write-off-btn"
                                    class="btn btn-sm btn-outline-danger"
                                    [attr.aria-label]="'Write off invoice ' + inv.number"
                                    (click)="openWriteOffModal(inv)">
                              Write off
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
        </section>

        <!-- 4) Recent payments -->
        <section class="card border-0 shadow-sm" aria-label="Recent supplier payments">
          <div class="card-header bg-white p-3 border-bottom">
            <h2 class="h6 fw-bold mb-0 text-dark">Recent payments</h2>
          </div>
          <div class="card-body p-0">
            @if (statement()!.recentPayments.length === 0) {
              <p class="text-secondary small p-3 mb-0">No recent payments.</p>
            } @else {
              <div class="table-responsive">
                <table class="table table-sm align-middle mb-0" aria-label="Recent supplier payments">
                  <thead>
                    <tr>
                      <th scope="col">Number</th>
                      <th scope="col">Payment date</th>
                      <th scope="col">Currency</th>
                      <th scope="col" class="text-end">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (p of statement()!.recentPayments; track p.paymentUid) {
                      <tr>
                        <td class="font-monospace">
                          <a [routerLink]="['/cash/supplier-payments/uid', p.paymentUid]"
                             class="text-decoration-none">{{ p.number }}</a>
                        </td>
                        <td>{{ p.paymentDate | date:'mediumDate' }}</td>
                        <td>{{ p.currencyCode }}</td>
                        <td class="text-end font-monospace">{{ p.totalAmount | number:'1.0-2' }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        </section>

        <!-- 5) AP chase notes -->
        <section class="card border-0 shadow-sm" aria-label="AP chase notes">
          <div class="card-header bg-white p-3 border-bottom">
            <h2 class="h6 fw-bold mb-0 text-dark">AP chase notes</h2>
          </div>
          <div class="card-body p-3 d-flex flex-column gap-3">
            @if (noteLoadError()) {
              <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
                <i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>
                <span class="flex-grow-1">{{ noteLoadError() }}</span>
                <button type="button" class="btn-close btn-sm" aria-label="Dismiss"
                        (click)="noteLoadError.set(null)"></button>
              </div>
            }

            @if (canAddNote()) {
              <div class="d-flex flex-column gap-2">
                <label for="apNoteBody" class="form-label small fw-semibold text-secondary mb-0">
                  Add an AP chase note
                </label>
                <textarea data-testid="debt-ap-chase-note-add"
                          id="apNoteBody"
                          class="form-control"
                          rows="3"
                          maxlength="1000"
                          placeholder="What action did you take? Called supplier, agreed payment schedule…"
                          [(ngModel)]="noteDraft"
                          name="noteDraft"
                          aria-describedby="apNoteCharCount"></textarea>
                <p id="apNoteCharCount" class="small text-secondary mb-0">
                  {{ noteDraft.length }} / 1000
                </p>
                @if (noteError()) {
                  <p class="small text-danger mb-0" role="alert">{{ noteError() }}</p>
                }
                <div>
                  <button data-testid="debt-ap-chase-note-save"
                          type="button"
                          class="btn btn-primary btn-sm"
                          [disabled]="savingNote() || !noteDraft.trim()"
                          (click)="saveNote()">
                    @if (savingNote()) {
                      <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
                    }
                    <i class="bi bi-plus-lg" aria-hidden="true"></i> Add note
                  </button>
                </div>
              </div>
            }

            @if (notesLoading()) {
              <div class="text-center text-secondary small py-2">
                <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span> Loading notes…
              </div>
            } @else {
              <ul data-testid="debt-ap-chase-notes-list" class="list-unstyled mb-0 d-flex flex-column gap-2"
                  aria-label="AP chase notes list">
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
                      @if (canArchiveNote() && n.status !== 'ARCHIVED') {
                        <button type="button"
                                class="btn btn-sm btn-outline-secondary"
                                [attr.aria-label]="'Archive note from ' + (n.createdAt | date:'medium')"
                                [disabled]="archivingNoteUid() === n.uid"
                                (click)="archiveNote(n.uid)">
                          @if (archivingNoteUid() === n.uid) {
                            <span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>
                          }
                          Archive
                        </button>
                      }
                    </div>
                    <p class="mb-0">{{ n.body }}</p>
                  </li>
                } @empty {
                  <li class="text-secondary small">No AP chase notes yet for this supplier.</li>
                }
              </ul>
            }
          </div>
        </section>

      </div>
    }

    <!-- Write-off modal (Slice G.2) -->
    <orbix-debt-write-off-modal
      [visible]="writeOffModalVisible()"
      [targetKind]="'SUPPLIER_INVOICE'"
      [targetInvoiceUid]="writeOffInvoiceUid()"
      [targetInvoiceNumber]="writeOffInvoiceNumber()"
      [outstanding]="writeOffOutstanding()"
      (writeOffCreated)="onWriteOffCreated()"
      (closed)="closeWriteOffModal()">
    </orbix-debt-write-off-modal>
  `,
  styles: [`
    :host { display: block; }
    .bucket-cell {
      padding: 1rem 0.75rem;
      text-align: center;
      border-right: 1px solid #f1f5f9;
      vertical-align: middle;
    }
    .bucket-cell:last-child { border-right: none; }
    .bucket-label {
      font-size: 0.7rem;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: #64748b;
      margin-bottom: 0.25rem;
    }
    .bucket-amount {
      font-family: 'Source Code Pro', SFMono-Regular, Menlo, monospace;
      font-size: 1.05rem;
      font-weight: 700;
      color: #0f172a;
    }
  `]
})
export class DebtSupplierComponent implements OnInit {
  private readonly debt = inject(DebtService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly permissionDenied = signal(false);

  protected readonly statement = signal<SupplierStatement | null>(null);
  protected readonly notes = signal<PartyNote[]>([]);
  protected readonly notesLoading = signal(false);
  protected readonly noteLoadError = signal<string | null>(null);

  // Chase-note append form
  protected readonly savingNote = signal(false);
  protected readonly noteError = signal<string | null>(null);
  protected noteDraft = '';

  // Archive state — tracks which note uid is being archived
  protected readonly archivingNoteUid = signal<string | null>(null);

  // Write-off modal state (Slice G.2)
  protected readonly writeOffModalVisible = signal(false);
  protected readonly writeOffInvoiceUid = signal('');
  protected readonly writeOffInvoiceNumber = signal<string | null>(null);
  protected readonly writeOffOutstanding = signal(0);

  protected readonly canAddNote = computed(() =>
    this.auth.hasPermission('DEBT.NOTE.CREATE')
  );
  protected readonly canArchiveNote = computed(() =>
    this.auth.hasPermission('DEBT.NOTE.ARCHIVE')
  );
  protected readonly canRequestWriteOff = computed(() =>
    this.auth.hasPermission('DEBT.WRITE_OFF.REQUEST')
  );

  private supplierUid = '';

  ngOnInit(): void {
    this.route.paramMap.subscribe(pm => {
      const uid = pm.get('uid');
      if (!uid) return;
      this.supplierUid = uid;
      this.load();
    });
  }

  /** URL for the full supplier statement report. Uses numeric id from the DTO. */
  protected statementUrl(): string {
    const s = this.statement();
    if (!s) return '/reports/supplier-statement';
    return `/reports/supplier-statement?supplierId=${s.supplierId}`;
  }

  /** Compute 5-bucket amounts inline from the openInvoices list. */
  protected agingBuckets(): { key: AgingBucket; label: string; amount: number }[] {
    const invoices = this.statement()?.openInvoices ?? [];

    let current = 0, d1_30 = 0, d31_60 = 0, d61_90 = 0, d90plus = 0;

    for (const inv of invoices) {
      const outstanding = inv.outstanding;
      const days = inv.daysOverdue ?? 0;

      if (days <= 0) {
        current += outstanding;
      } else if (days <= 30) {
        d1_30 += outstanding;
      } else if (days <= 60) {
        d31_60 += outstanding;
      } else if (days <= 90) {
        d61_90 += outstanding;
      } else {
        d90plus += outstanding;
      }
    }

    return [
      { key: 'CURRENT',   label: 'Current',     amount: current  },
      { key: 'D_1_30',    label: '1 – 30 days',  amount: d1_30   },
      { key: 'D_31_60',   label: '31 – 60 days', amount: d31_60  },
      { key: 'D_61_90',   label: '61 – 90 days', amount: d61_90  },
      { key: 'D_90_PLUS', label: '90+ days',      amount: d90plus },
    ];
  }

  protected saveNote(): void {
    const body = this.noteDraft.trim();
    if (!body) return;
    this.noteError.set(null);
    this.savingNote.set(true);
    this.debt.createNote({
      customerUid: this.supplierUid,
      kind: 'AP_CHASE',
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

  protected archiveNote(noteUid: string): void {
    this.archivingNoteUid.set(noteUid);
    this.debt.archiveNote(noteUid).subscribe({
      next: updated => {
        this.notes.update(ns => ns.map(n => n.uid === noteUid ? updated : n));
        this.archivingNoteUid.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.noteLoadError.set(this.formatError(err, 'Could not archive the note.'));
        this.archivingNoteUid.set(null);
      },
    });
  }

  /** Open the write-off modal seeded with the invoice row's data. */
  protected openWriteOffModal(inv: OpenSupplierInvoiceRow): void {
    this.writeOffInvoiceUid.set(inv.invoiceUid);
    this.writeOffInvoiceNumber.set(inv.number);
    this.writeOffOutstanding.set(inv.outstanding);
    this.writeOffModalVisible.set(true);
  }

  protected closeWriteOffModal(): void {
    this.writeOffModalVisible.set(false);
  }

  /** After a successful write-off, refresh the statement so the table updates. */
  protected onWriteOffCreated(): void {
    this.writeOffModalVisible.set(false);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.permissionDenied.set(false);

    forkJoin({
      statement: this.debt.supplierStatement(this.supplierUid),
      notes: this.debt.listNotes(this.supplierUid, { limit: 50, kind: 'AP_CHASE' }),
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
          this.error.set(this.formatError(err, 'Failed to load supplier position.'));
        }
        this.loading.set(false);
      },
    });
  }

  private reloadNotes(): void {
    this.notesLoading.set(true);
    this.debt.listNotes(this.supplierUid, { limit: 50, kind: 'AP_CHASE' }).subscribe({
      next: notes => {
        this.notes.set(notes);
        this.notesLoading.set(false);
      },
      error: () => {
        this.notesLoading.set(false);
        /* preserve existing list */
      },
    });
  }

  private formatError(err: HttpErrorResponse, fallback: string): string {
    const body = err.error as Record<string, unknown> | null;
    if (body && typeof body['message'] === 'string' && body['message']) {
      return body['message'];
    }
    return fallback;
  }
}

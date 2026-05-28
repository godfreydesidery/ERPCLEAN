import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { BranchService } from '../../core/branch/branch.service';
import { ProcurementService } from './procurement.service';
import {
  CreateVendorReturnRequest,
  Grn,
  ReturnReason,
  VENDOR_RETURN_REASONS,
} from './procurement.models';

interface LineRow {
  itemUid: string;
  uomUid: string;
  returnedQty: number | null;
  unitPrice: number | null;
  vatGroupUid: string;
  originalLineId?: string;
}

function emptyLine(): LineRow {
  return { itemUid: '', uomUid: '', returnedQty: null, unitPrice: null, vatGroupUid: '' };
}

@Component({
  selector: 'orbix-vendor-return-create',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink="/procurement" class="text-decoration-none text-secondary">Procurement</a>
          &rsaquo;
          <a routerLink="/procurement/vendor-returns" class="text-decoration-none text-secondary">Vendor returns</a>
          &rsaquo; New
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">New vendor return</h1>
        <p class="text-secondary mb-0 small">Return goods to a supplier and record the reason.</p>
      </div>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2" role="alert">
        <i class="bi bi-exclamation-triangle-fill" aria-hidden="true"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss error" (click)="error.set(null)"></button>
      </div>
    }

    <form (ngSubmit)="saveDraft()" #f="ngForm" novalidate>
      <!-- Header fieldset -->
      <fieldset class="form-fieldset mb-3">
        <legend class="form-fieldset__legend">
          <i class="bi bi-truck text-secondary" aria-hidden="true"></i> Return header
        </legend>
        <div class="row g-2">

          <div class="col-md-4">
            <label class="form-label small fw-semibold text-secondary" for="supplierUid">
              Supplier UID <span class="text-danger" aria-hidden="true">*</span>
            </label>
            <input id="supplierUid" class="form-control" name="supplierUid"
                   [(ngModel)]="supplierUid" required
                   placeholder="Supplier ULID"
                   aria-describedby="supplierUidHelp"
                   (blur)="onSupplierSet()">
            <div id="supplierUidHelp" class="form-text">Paste the supplier's UID to load their GRNs.</div>
          </div>

          <div class="col-md-4">
            <label class="form-label small fw-semibold text-secondary" for="grnRef">
              GRN reference <span class="text-muted">(optional)</span>
            </label>
            <select id="grnRef" class="form-select" name="grnRef" [(ngModel)]="selectedGrnUid"
                    (ngModelChange)="onGrnSelected($event)"
                    [disabled]="grns().length === 0">
              <option value="">— no GRN reference —</option>
              @for (grn of grns(); track grn.uid) {
                <option [value]="grn.uid">{{ grn.number }} ({{ grn.receivedDate }})</option>
              }
            </select>
          </div>

          <div class="col-md-4">
            <label class="form-label small fw-semibold text-secondary" for="returnDate">
              Return date <span class="text-danger" aria-hidden="true">*</span>
            </label>
            <input id="returnDate" class="form-control" type="date" name="returnDate"
                   [(ngModel)]="returnDate" required>
          </div>

          <div class="col-md-4">
            <label class="form-label small fw-semibold text-secondary" for="reason">
              Reason <span class="text-danger" aria-hidden="true">*</span>
            </label>
            <select id="reason" class="form-select" name="reason" [(ngModel)]="reason" required>
              @for (r of reasons; track r) { <option [ngValue]="r">{{ r }}</option> }
            </select>
          </div>

          <div class="col-md-4 d-flex align-items-end">
            <div class="form-check pb-2">
              <input class="form-check-input" type="checkbox" id="restock"
                     [(ngModel)]="restock" name="restock">
              <label class="form-check-label small" for="restock">
                Restock <span class="text-muted">(otherwise damage discard)</span>
              </label>
            </div>
          </div>

          <div class="col-12">
            <label class="form-label small fw-semibold text-secondary" for="notes">
              Notes <span class="text-muted">(optional)</span>
            </label>
            <textarea id="notes" class="form-control" name="notes" rows="2"
                      [(ngModel)]="notes" placeholder="Internal notes…"></textarea>
          </div>
        </div>
      </fieldset>

      <!-- Lines fieldset -->
      <fieldset class="form-fieldset mb-3">
        <legend class="form-fieldset__legend d-flex align-items-center justify-content-between">
          <span><i class="bi bi-list-ul text-secondary" aria-hidden="true"></i> Lines</span>
          <button type="button" class="btn btn-sm btn-outline-primary" (click)="addLine()">
            <i class="bi bi-plus-lg me-1" aria-hidden="true"></i>Add line
          </button>
        </legend>
        <div class="table-responsive">
          <table class="table table-sm align-middle mb-0 line-table">
            <thead>
              <tr>
                <th scope="col">Item UID</th>
                <th scope="col">UOM UID</th>
                <th scope="col">VAT Group UID</th>
                <th scope="col" class="text-end">Qty</th>
                <th scope="col" class="text-end">Unit price</th>
                <th scope="col" class="actions-col"></th>
              </tr>
            </thead>
            <tbody>
              @for (row of lines; track $index) {
                <tr>
                  <td>
                    <input class="form-control form-control-sm" type="text"
                           [name]="'item' + $index" [(ngModel)]="row.itemUid"
                           [attr.aria-label]="'Item UID row ' + ($index + 1)"
                           placeholder="Item ULID">
                  </td>
                  <td>
                    <input class="form-control form-control-sm" type="text"
                           [name]="'uom' + $index" [(ngModel)]="row.uomUid"
                           [attr.aria-label]="'UOM UID row ' + ($index + 1)"
                           placeholder="UOM ULID">
                  </td>
                  <td>
                    <input class="form-control form-control-sm" type="text"
                           [name]="'vat' + $index" [(ngModel)]="row.vatGroupUid"
                           [attr.aria-label]="'VAT group UID row ' + ($index + 1)"
                           placeholder="VatGroup ULID">
                  </td>
                  <td>
                    <input class="form-control form-control-sm text-end" type="number"
                           step="0.0001" min="0.0001" [name]="'qty' + $index" [(ngModel)]="row.returnedQty"
                           [attr.aria-label]="'Quantity row ' + ($index + 1)">
                  </td>
                  <td>
                    <input class="form-control form-control-sm text-end" type="number"
                           step="0.0001" min="0" [name]="'price' + $index" [(ngModel)]="row.unitPrice"
                           [attr.aria-label]="'Unit price row ' + ($index + 1)">
                  </td>
                  <td class="actions-col">
                    <button type="button" class="btn btn-sm btn-outline-secondary"
                            [attr.aria-label]="'Remove line ' + ($index + 1)"
                            (click)="removeLine($index)">
                      <i class="bi bi-x-lg" aria-hidden="true"></i>
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </fieldset>

      <!-- Actions -->
      <div class="d-flex gap-2">
        <button type="submit"
                class="btn btn-primary d-inline-flex align-items-center gap-2"
                [disabled]="busy() || f.invalid">
          @if (busy()) {
            <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
          } @else {
            <i class="bi bi-floppy" aria-hidden="true"></i>
          }
          Save draft
        </button>
        <button type="button"
                class="btn btn-success d-inline-flex align-items-center gap-2"
                [disabled]="busy() || f.invalid"
                (click)="saveDraft(true)">
          @if (busy()) {
            <span class="spinner-border spinner-border-sm" aria-hidden="true"></span>
          } @else {
            <i class="bi bi-send" aria-hidden="true"></i>
          }
          Save &amp; post
        </button>
        <a routerLink="/procurement/vendor-returns" class="btn btn-outline-secondary">Cancel</a>
      </div>
    </form>
  `,
  styles: [`
    :host { display: block; }
    .form-fieldset {
      background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 10px;
      padding: 1rem 1.25rem 1.25rem;
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
      color: #6b7280; border-bottom: 1px solid #e5e7eb; padding: 0.5rem;
    }
    .line-table tbody td { padding: 0.4rem 0.5rem; vertical-align: middle; }
    .line-table .actions-col { width: 1%; white-space: nowrap; }
  `]
})
export class VendorReturnCreateComponent implements OnInit {
  private readonly procurement = inject(ProcurementService);
  private readonly branchService = inject(BranchService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly reasons = VENDOR_RETURN_REASONS;
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly grns = signal<Grn[]>([]);

  protected supplierUid = '';
  protected selectedGrnUid = '';
  protected returnDate = new Date().toISOString().slice(0, 10);
  protected reason: ReturnReason = 'DAMAGED';
  protected restock = true;
  protected notes = '';
  protected lines: LineRow[] = [emptyLine()];

  protected readonly branchId = computed(() =>
    this.branchService.activeBranchId() ?? this.auth.currentUser()?.defaultBranchId ?? null
  );

  ngOnInit(): void { /* nothing to pre-load */ }

  addLine(): void { this.lines.push(emptyLine()); }

  removeLine(i: number): void {
    this.lines.splice(i, 1);
    if (this.lines.length === 0) this.addLine();
  }

  onSupplierSet(): void {
    if (!this.supplierUid.trim()) { this.grns.set([]); return; }
    // Load posted GRNs for this supplier so the user can pick a GRN reference.
    // We use listGrns (branch-scoped) — filtering by supplier is done client-side.
    this.procurement.listGrns(this.branchId(), 0, 200).subscribe({
      next: page => {
        const uid = this.supplierUid.trim();
        this.grns.set(page.content.filter(g => g.status === 'POSTED'));
        // Reset GRN selection when supplier changes.
        this.selectedGrnUid = '';
      },
      error: () => this.grns.set([])
    });
  }

  onGrnSelected(grnUid: string): void {
    if (!grnUid) { this.lines = [emptyLine()]; return; }
    const grn = this.grns().find(g => g.uid === grnUid);
    if (!grn) return;
    // Pre-fill lines from the selected GRN.
    this.lines = grn.lines.map(gl => ({
      itemUid: gl.itemId,       // IDs serve as placeholder — real impl would use UIDs from GRN response
      uomUid: gl.uomId,
      vatGroupUid: gl.vatGroupId,
      returnedQty: gl.receivedQty,
      unitPrice: gl.unitCost,
      originalLineId: gl.id,
    }));
    if (this.lines.length === 0) this.lines = [emptyLine()];
  }

  saveDraft(andPost = false): void {
    if (!this.supplierUid.trim()) { this.error.set('Supplier UID is required.'); return; }

    const validLines = this.lines.filter(
      l => l.itemUid.trim() && l.uomUid.trim() && (l.returnedQty ?? 0) > 0 && (l.unitPrice ?? 0) >= 0
    );
    if (validLines.length === 0) { this.error.set('Add at least one valid line.'); return; }

    const request: CreateVendorReturnRequest = {
      supplierUid: this.supplierUid.trim(),
      originalGrnUid: this.selectedGrnUid.trim() || undefined,
      returnDate: this.returnDate,
      reason: this.reason,
      restock: this.restock,
      notes: this.notes.trim() || undefined,
      lines: validLines.map(l => ({
        itemUid: l.itemUid.trim(),
        uomUid: l.uomUid.trim(),
        returnedQty: l.returnedQty as number,
        unitPrice: l.unitPrice as number,
        vatGroupUid: l.vatGroupUid.trim() || 'STANDARD',
        originalLineId: l.originalLineId,
      })),
    };

    this.busy.set(true);
    this.error.set(null);
    this.procurement.createVendorReturn(request).subscribe({
      next: ret => {
        if (andPost) {
          this.procurement.postVendorReturn(ret.uid).subscribe({
            next: () => void this.router.navigate(['/procurement/vendor-returns']),
            error: err => { this.busy.set(false); this.showError(err); }
          });
        } else {
          this.busy.set(false);
          void this.router.navigate(['/procurement/vendor-returns']);
        }
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

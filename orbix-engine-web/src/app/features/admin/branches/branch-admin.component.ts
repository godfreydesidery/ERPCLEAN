import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { BranchAdminService } from './branch-admin.service';
import { Branch, SECTION_TYPES, Section } from './branch-admin.models';

@Component({
  selector: 'orbix-branch-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Admin</a> &rsaquo; Branches
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Branches &amp; sections</h1>
        <p class="text-secondary mb-0 small">{{ branches().length }} branch{{ branches().length === 1 ? '' : 'es' }} configured.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm" (click)="toggleNewBranch()">
        <i class="bi" [class.bi-plus-lg]="!showNewBranch()" [class.bi-x-lg]="showNewBranch()"></i>
        {{ showNewBranch() ? 'Close form' : 'New branch' }}
      </button>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i><span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" (click)="error.set(null)"></button>
      </div>
    }

    @if (showNewBranch()) {
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
          <h2 class="h6 fw-bold mb-0 text-dark">New branch</h2>
          <button class="btn-close btn-sm" (click)="toggleNewBranch()"></button>
        </div>
        <div class="card-body p-3">
          <form (ngSubmit)="createBranch()" #bf="ngForm" class="d-flex flex-column gap-3">
            <div class="row g-2">
              <div class="col-md-3">
                <label class="form-label small fw-semibold text-secondary">Code</label>
                <input class="form-control font-monospace" name="code" [(ngModel)]="newBranch.code" required placeholder="HQ">
              </div>
              <div class="col-md-5">
                <label class="form-label small fw-semibold text-secondary">Name</label>
                <input class="form-control" name="name" [(ngModel)]="newBranch.name" required>
              </div>
              <div class="col-md-4">
                <label class="form-label small fw-semibold text-secondary">Type</label>
                <input class="form-control" name="type" [(ngModel)]="newBranch.type" required>
              </div>
              <div class="col-12">
                <label class="form-label small fw-semibold text-secondary">Time zone</label>
                <input class="form-control font-monospace" name="tz" [(ngModel)]="newBranch.timeZone" required placeholder="Africa/Dar_es_Salaam">
              </div>
            </div>
            <div class="d-flex gap-2 pt-2 border-top">
              <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                      [disabled]="saving() || bf.invalid">
                @if (saving()) { <span class="spinner-border spinner-border-sm"></span> }
                @else { <i class="bi bi-plus-lg"></i> }
                Create branch
              </button>
              <button type="button" class="btn btn-outline-secondary" (click)="toggleNewBranch()">Cancel</button>
            </div>
          </form>
        </div>
      </div>
    }

    <div class="row g-3 g-md-4">
      <!-- Branch list -->
      <div class="col-12 col-lg-4">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">Branches</h2>
            <span class="badge text-bg-light text-secondary">{{ branches().length }}</span>
          </div>
          @if (branches().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-building"></i></div>
              <p class="small text-secondary mb-0">No branches yet.</p>
            </div>
          } @else {
            <ul class="list-unstyled mb-0 br-list">
              @for (branch of branches(); track branch.id) {
                <li>
                  <button type="button" class="br-row"
                          [class.is-active]="selected()?.id === branch.id"
                          (click)="selectBranch(branch)">
                    <div class="flex-grow-1 min-w-0">
                      <p class="fw-semibold text-dark mb-0 text-truncate">{{ branch.name }}</p>
                      <p class="small text-secondary mb-0">
                        <span class="font-monospace">{{ branch.code }}</span> · {{ branch.type }}
                      </p>
                    </div>
                    <div class="d-flex flex-column align-items-end gap-1">
                      @if (branch.isDefault) {
                        <span class="badge text-bg-primary-subtle text-primary">DEFAULT</span>
                      }
                      <span class="status-badge status-badge--{{ branch.status.toLowerCase() }}">
                        <span class="status-badge__dot"></span>{{ branch.status }}
                      </span>
                    </div>
                  </button>
                </li>
              }
            </ul>
          }
        </div>
      </div>

      <!-- Selected branch detail -->
      <div class="col-12 col-lg-8">
        @if (selected(); as branch) {
          <div class="card border-0 shadow-sm mb-3">
            <div class="card-body p-4">
              <div class="d-flex flex-wrap align-items-start justify-content-between gap-3 mb-3">
                <div>
                  <p class="small text-secondary mb-1">
                    <span class="font-monospace">{{ branch.code }}</span> · {{ branch.type }}
                  </p>
                  <h2 class="h4 fw-bold mb-1 text-dark">{{ branch.name }}</h2>
                  <span class="status-badge status-badge--{{ branch.status.toLowerCase() }}">
                    <span class="status-badge__dot"></span>{{ branch.status }}
                  </span>
                </div>
                @if (branch.status === 'ACTIVE') {
                  <button class="btn btn-sm btn-outline-danger d-inline-flex align-items-center gap-1"
                          (click)="deactivateBranch(branch)" [disabled]="saving()">
                    <i class="bi bi-pause-circle"></i> Deactivate
                  </button>
                }
              </div>

              <form (ngSubmit)="saveBranch(branch)" #ef="ngForm" class="d-flex flex-column gap-3">
                <fieldset class="form-fieldset">
                  <legend class="form-fieldset__legend"><i class="bi bi-building text-secondary"></i> Branch details</legend>
                  <div class="row g-2">
                    <div class="col-md-6">
                      <label class="form-label small fw-semibold text-secondary">Name</label>
                      <input class="form-control" name="ename" [(ngModel)]="editBranch.name" required>
                    </div>
                    <div class="col-md-6">
                      <label class="form-label small fw-semibold text-secondary">Type</label>
                      <input class="form-control" name="etype" [(ngModel)]="editBranch.type" required>
                    </div>
                    <div class="col-md-6">
                      <label class="form-label small fw-semibold text-secondary">Phone</label>
                      <input class="form-control" name="ephone" [(ngModel)]="editBranch.phone">
                    </div>
                    <div class="col-md-6">
                      <label class="form-label small fw-semibold text-secondary">Time zone</label>
                      <input class="form-control font-monospace" name="etz" [(ngModel)]="editBranch.timeZone" required>
                    </div>
                    <div class="col-12">
                      <label class="form-label small fw-semibold text-secondary">Physical address</label>
                      <textarea class="form-control" name="eaddr" rows="2"
                                [(ngModel)]="editBranch.physicalAddress"></textarea>
                    </div>
                  </div>
                </fieldset>
                <div class="d-flex">
                  <button class="btn btn-outline-primary btn-sm d-inline-flex align-items-center gap-1"
                          [disabled]="saving() || ef.invalid">
                    <i class="bi bi-save"></i> Save branch details
                  </button>
                </div>
              </form>
            </div>
          </div>

          <!-- Sections -->
          <div class="card border-0 shadow-sm overflow-hidden">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h3 class="h6 fw-bold mb-0 text-dark">Sections</h3>
              <span class="badge text-bg-light text-secondary">{{ sections().length }}</span>
            </div>
            <div class="table-responsive">
              <table class="table table-hover align-middle mb-0 simple-table">
                <thead>
                  <tr>
                    <th>Code</th><th>Name</th><th>Type</th><th>Status</th>
                    <th class="text-end actions-col"></th>
                  </tr>
                </thead>
                <tbody>
                  @for (section of sections(); track section.id) {
                    <tr [class.table-active]="editingSectionId() === section.uid">
                      <td><span class="badge text-bg-light border text-secondary font-monospace">{{ section.code }}</span></td>
                      <td class="fw-semibold text-dark">{{ section.name }}</td>
                      <td class="small text-secondary">{{ section.type }}</td>
                      <td>
                        <span class="status-badge status-badge--{{ section.status.toLowerCase() }}">
                          <span class="status-badge__dot"></span>{{ section.status }}
                        </span>
                      </td>
                      <td class="text-end actions-col">
                        <div class="btn-group btn-group-sm">
                          <button class="btn btn-outline-secondary" (click)="editSection(section)"
                                  [disabled]="saving()" title="Edit">
                            <i class="bi bi-pencil"></i>
                          </button>
                          @if (section.status === 'ACTIVE') {
                            <button class="btn btn-outline-danger" (click)="deactivateSection(section)"
                                    [disabled]="saving()" title="Deactivate">
                              <i class="bi bi-pause-circle"></i>
                            </button>
                          }
                        </div>
                      </td>
                    </tr>
                  } @empty {
                    <tr><td colspan="5" class="text-center text-secondary py-4">No sections.</td></tr>
                  }
                </tbody>
              </table>
            </div>

            <div class="card-footer bg-white border-top p-3">
              <form (ngSubmit)="submitSection(branch)" #sf="ngForm" class="d-flex flex-column gap-2">
                <p class="small fw-semibold text-secondary mb-0">
                  {{ editingSectionId() ? 'Edit section' : 'New section' }}
                </p>
                <div class="row g-2 align-items-end">
                  @if (!editingSectionId()) {
                    <div class="col-md-3">
                      <label class="form-label small fw-semibold text-secondary">Code</label>
                      <input class="form-control font-monospace" name="scode" [(ngModel)]="sectionForm.code" required>
                    </div>
                  }
                  <div class="col-md-4">
                    <label class="form-label small fw-semibold text-secondary">Name</label>
                    <input class="form-control" name="sname" [(ngModel)]="sectionForm.name" required>
                  </div>
                  <div class="col-md-3">
                    <label class="form-label small fw-semibold text-secondary">Type</label>
                    <select class="form-select" name="stype" [(ngModel)]="sectionForm.type" required>
                      @for (t of sectionTypes; track t) { <option [value]="t">{{ t }}</option> }
                    </select>
                  </div>
                  <div class="col-md d-flex gap-2">
                    <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-1"
                            [disabled]="saving() || sf.invalid">
                      <i class="bi" [class.bi-save]="editingSectionId()" [class.bi-plus-lg]="!editingSectionId()"></i>
                      {{ editingSectionId() ? 'Save' : 'Add' }}
                    </button>
                    @if (editingSectionId()) {
                      <button type="button" class="btn btn-outline-secondary" (click)="cancelSectionEdit()">
                        Cancel
                      </button>
                    }
                  </div>
                </div>
              </form>
            </div>
          </div>
        } @else {
          <div class="card border-0 shadow-sm">
            <div class="card-body p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cursor"></i></div>
              <h2 class="h6 fw-bold mb-1 text-dark">Pick a branch</h2>
              <p class="small text-secondary mb-0">Or create a new one to start managing sections.</p>
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
      color: #374151; padding: 0 0.5rem; width: auto; margin-bottom: 0.5rem;
    }
    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8; box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }

    .br-list { max-height: 70vh; overflow-y: auto; }
    .br-row {
      width: 100%; display: flex; align-items: center; gap: 0.75rem;
      padding: 0.875rem 1rem; background: #fff; border: none;
      border-bottom: 1px solid #f3f4f6; text-align: left;
      transition: background 0.1s ease;
    }
    .br-row:hover { background: #f8fafc; }
    .br-row.is-active { background: #eef4ff; border-left: 3px solid #1d4ed8; padding-left: calc(1rem - 3px); }
    .br-row:last-child { border-bottom: none; }

    .simple-table thead th {
      font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;
      color: #6b7280; background: #f9fafb; border-bottom: 1px solid #e5e7eb; padding: 0.75rem 1rem;
    }
    .simple-table tbody td { padding: 0.75rem 1rem; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
    .simple-table tbody tr:last-child td { border-bottom: none; }
    .simple-table tbody tr:hover { background: #f8fafc; }
    .simple-table tbody tr.table-active { background: #eef4ff !important; }
    .simple-table .actions-col { width: 1%; white-space: nowrap; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.2rem 0.55rem; border-radius: 999px;
      font-size: 0.7rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 5px; height: 5px; border-radius: 50%; }
    .status-badge--active   { background: #d1fae5; color: #047857; }
    .status-badge--active .status-badge__dot   { background: #10b981; }
    .status-badge--inactive { background: #f3f4f6; color: #4b5563; }
    .status-badge--inactive .status-badge__dot { background: #9ca3af; }
    .status-badge--archived { background: #fee2e2; color: #b91c1c; }
    .status-badge--archived .status-badge__dot { background: #f43f5e; }

    .text-bg-primary-subtle { background: #e0ecff; color: #1d4ed8; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #e0ecff; color: #1d4ed8; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }
  `]
})
export class BranchAdminComponent implements OnInit {
  private readonly api = inject(BranchAdminService);

  protected readonly sectionTypes = SECTION_TYPES;

  protected readonly branches = signal<Branch[]>([]);
  protected readonly selected = signal<Branch | null>(null);
  protected readonly sections = signal<Section[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly editingSectionId = signal<string | null>(null);
  protected readonly showNewBranch = signal(false);

  protected newBranch = blankBranchForm();
  protected editBranch = blankBranchForm();
  protected sectionForm = blankSectionForm();

  ngOnInit(): void { this.loadBranches(); }

  toggleNewBranch(): void {
    this.showNewBranch.update(v => !v);
    if (!this.showNewBranch()) this.newBranch = blankBranchForm();
  }

  selectBranch(branch: Branch): void {
    this.selected.set(branch);
    this.editBranch = {
      code: branch.code,
      name: branch.name,
      type: branch.type,
      phone: branch.phone ?? '',
      timeZone: branch.timeZone,
      physicalAddress: branch.physicalAddress ?? ''
    };
    this.cancelSectionEdit();
    this.loadSections(branch.uid);
  }

  createBranch(): void {
    this.run(this.api.createBranch({
      code: this.newBranch.code.trim(),
      name: this.newBranch.name.trim(),
      type: this.newBranch.type.trim(),
      physicalAddress: null,
      phone: null,
      timeZone: this.newBranch.timeZone.trim()
    }), branch => {
      this.newBranch = blankBranchForm();
      this.showNewBranch.set(false);
      this.loadBranches();
      this.selectBranch(branch);
    });
  }

  saveBranch(branch: Branch): void {
    this.run(this.api.updateBranch(branch.uid, {
      name: this.editBranch.name.trim(),
      type: this.editBranch.type.trim(),
      phone: emptyToNull(this.editBranch.phone),
      timeZone: this.editBranch.timeZone.trim(),
      physicalAddress: emptyToNull(this.editBranch.physicalAddress)
    }), updated => {
      this.loadBranches();
      this.selectBranch(updated);
    });
  }

  deactivateBranch(branch: Branch): void {
    this.run(this.api.deactivateBranch(branch.uid), () => {
      this.loadBranches();
      this.selected.set({ ...branch, status: 'INACTIVE' });
    });
  }

  editSection(section: Section): void {
    this.editingSectionId.set(section.uid);
    this.sectionForm = { code: section.code, name: section.name, type: section.type };
  }

  cancelSectionEdit(): void {
    this.editingSectionId.set(null);
    this.sectionForm = blankSectionForm();
  }

  submitSection(branch: Branch): void {
    const editingId = this.editingSectionId();
    const onDone = () => {
      this.cancelSectionEdit();
      this.loadSections(branch.uid);
    };
    const call = editingId === null
      ? this.api.createSection(branch.uid, {
          code: this.sectionForm.code.trim(),
          name: this.sectionForm.name.trim(),
          type: this.sectionForm.type,
          managerUserId: null
        })
      : this.api.updateSection(editingId, {
          name: this.sectionForm.name.trim(),
          type: this.sectionForm.type,
          managerUserId: null
        });
    this.run(call, onDone);
  }

  deactivateSection(section: Section): void {
    this.run(this.api.deactivateSection(section.uid), () => this.loadSections(this.selected()!.uid));
  }

  private loadBranches(): void {
    this.api.listBranches().subscribe({
      next: branches => this.branches.set(branches),
      error: err => this.showError(err)
    });
  }

  private loadSections(branchId: string): void {
    this.api.listSections(branchId).subscribe({
      next: sections => this.sections.set(sections),
      error: err => this.showError(err)
    });
  }

  private run<T>(source: Observable<T>, onSuccess: (value: T) => void): void {
    this.saving.set(true);
    this.error.set(null);
    source.subscribe({
      next: value => { this.saving.set(false); onSuccess(value); },
      error: err => { this.saving.set(false); this.showError(err); }
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

function blankBranchForm() {
  return { code: '', name: '', type: 'RETAIL', phone: '', timeZone: 'Africa/Dar_es_Salaam', physicalAddress: '' };
}

function blankSectionForm() {
  return { code: '', name: '', type: 'RETAIL_FLOOR' as string };
}

function emptyToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

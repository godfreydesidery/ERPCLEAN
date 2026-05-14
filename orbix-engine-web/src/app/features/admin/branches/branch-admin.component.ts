import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { BranchAdminService } from './branch-admin.service';
import { Branch, SECTION_TYPES, Section } from './branch-admin.models';

@Component({
  selector: 'orbix-branch-admin',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2 class="h3 mb-4">Branches &amp; sections</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <div class="row g-4">
      <!-- Branch list + create -->
      <div class="col-12 col-lg-4">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Branches</div>
          <div class="list-group list-group-flush">
            @for (branch of branches(); track branch.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between align-items-center"
                      [class.active]="selected()?.id === branch.id"
                      (click)="selectBranch(branch)">
                <span>
                  <span class="fw-semibold">{{ branch.name }}</span>
                  <small class="d-block text-muted">{{ branch.code }} · {{ branch.type }}</small>
                </span>
                <span>
                  @if (branch.isDefault) { <span class="badge text-bg-secondary me-1">default</span> }
                  @if (branch.status !== 'ACTIVE') { <span class="badge text-bg-warning">{{ branch.status }}</span> }
                </span>
              </button>
            } @empty {
              <div class="list-group-item text-muted">No branches yet.</div>
            }
          </div>
        </div>

        <div class="card shadow-sm mt-3">
          <div class="card-header fw-semibold">New branch</div>
          <div class="card-body">
            <form (ngSubmit)="createBranch()" #bf="ngForm">
              <div class="mb-2">
                <label class="form-label">Code</label>
                <input class="form-control" name="code" [(ngModel)]="newBranch.code" required>
              </div>
              <div class="mb-2">
                <label class="form-label">Name</label>
                <input class="form-control" name="name" [(ngModel)]="newBranch.name" required>
              </div>
              <div class="mb-2">
                <label class="form-label">Type</label>
                <input class="form-control" name="type" [(ngModel)]="newBranch.type" required>
              </div>
              <div class="mb-2">
                <label class="form-label">Time zone</label>
                <input class="form-control" name="tz" [(ngModel)]="newBranch.timeZone" required>
              </div>
              <button class="btn btn-primary w-100" [disabled]="saving() || bf.invalid">Create branch</button>
            </form>
          </div>
        </div>
      </div>

      <!-- Selected branch detail -->
      <div class="col-12 col-lg-8">
        @if (selected(); as branch) {
          <div class="card shadow-sm">
            <div class="card-header d-flex justify-content-between align-items-center">
              <span class="fw-semibold">{{ branch.code }}</span>
              @if (branch.status === 'ACTIVE') {
                <button class="btn btn-sm btn-outline-danger" (click)="deactivateBranch(branch)"
                        [disabled]="saving()">Deactivate branch</button>
              } @else {
                <span class="badge text-bg-warning">{{ branch.status }}</span>
              }
            </div>
            <div class="card-body">
              <!-- Branch details -->
              <form (ngSubmit)="saveBranch(branch)" #ef="ngForm" class="row g-2 mb-4">
                <div class="col-md-6">
                  <label class="form-label">Name</label>
                  <input class="form-control" name="ename" [(ngModel)]="editBranch.name" required>
                </div>
                <div class="col-md-6">
                  <label class="form-label">Type</label>
                  <input class="form-control" name="etype" [(ngModel)]="editBranch.type" required>
                </div>
                <div class="col-md-6">
                  <label class="form-label">Phone</label>
                  <input class="form-control" name="ephone" [(ngModel)]="editBranch.phone">
                </div>
                <div class="col-md-6">
                  <label class="form-label">Time zone</label>
                  <input class="form-control" name="etz" [(ngModel)]="editBranch.timeZone" required>
                </div>
                <div class="col-12">
                  <label class="form-label">Physical address</label>
                  <textarea class="form-control" name="eaddr" rows="2"
                            [(ngModel)]="editBranch.physicalAddress"></textarea>
                </div>
                <div class="col-12">
                  <button class="btn btn-outline-primary btn-sm" [disabled]="saving() || ef.invalid">
                    Save branch details
                  </button>
                </div>
              </form>

              <!-- Sections -->
              <h3 class="h6">Sections</h3>
              <table class="table table-sm align-middle">
                <thead>
                  <tr><th>Code</th><th>Name</th><th>Type</th><th>Status</th><th></th></tr>
                </thead>
                <tbody>
                  @for (section of sections(); track section.id) {
                    <tr>
                      <td>{{ section.code }}</td>
                      <td>{{ section.name }}</td>
                      <td>{{ section.type }}</td>
                      <td>
                        @if (section.status === 'ACTIVE') {
                          <span class="badge text-bg-success">ACTIVE</span>
                        } @else {
                          <span class="badge text-bg-secondary">{{ section.status }}</span>
                        }
                      </td>
                      <td class="text-end">
                        <button class="btn btn-sm btn-outline-secondary me-1"
                                (click)="editSection(section)" [disabled]="saving()">Edit</button>
                        @if (section.status === 'ACTIVE') {
                          <button class="btn btn-sm btn-outline-danger"
                                  (click)="deactivateSection(section)" [disabled]="saving()">Deactivate</button>
                        }
                      </td>
                    </tr>
                  } @empty {
                    <tr><td colspan="5" class="text-muted">No sections.</td></tr>
                  }
                </tbody>
              </table>

              <!-- Section create / edit -->
              <form (ngSubmit)="submitSection(branch)" #sf="ngForm" class="row g-2 align-items-end">
                <div class="col-12">
                  <span class="fw-semibold small">
                    {{ editingSectionId() ? 'Edit section' : 'New section' }}
                  </span>
                </div>
                @if (!editingSectionId()) {
                  <div class="col-6 col-md-3">
                    <label class="form-label">Code</label>
                    <input class="form-control" name="scode" [(ngModel)]="sectionForm.code" required>
                  </div>
                }
                <div class="col-6 col-md-3">
                  <label class="form-label">Name</label>
                  <input class="form-control" name="sname" [(ngModel)]="sectionForm.name" required>
                </div>
                <div class="col-6 col-md-3">
                  <label class="form-label">Type</label>
                  <select class="form-select" name="stype" [(ngModel)]="sectionForm.type" required>
                    @for (t of sectionTypes; track t) {
                      <option [value]="t">{{ t }}</option>
                    }
                  </select>
                </div>
                <div class="col-6 col-md-3 d-flex gap-2">
                  <button class="btn btn-primary flex-grow-1" [disabled]="saving() || sf.invalid">
                    {{ editingSectionId() ? 'Save' : 'Add' }}
                  </button>
                  @if (editingSectionId()) {
                    <button type="button" class="btn btn-outline-secondary" (click)="cancelSectionEdit()">
                      Cancel
                    </button>
                  }
                </div>
              </form>
            </div>
          </div>
        } @else {
          <div class="text-muted">Select a branch to manage its details and sections.</div>
        }
      </div>
    </div>
  `
})
export class BranchAdminComponent implements OnInit {
  private readonly api = inject(BranchAdminService);

  readonly sectionTypes = SECTION_TYPES;

  readonly branches = signal<Branch[]>([]);
  readonly selected = signal<Branch | null>(null);
  readonly sections = signal<Section[]>([]);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly editingSectionId = signal<number | null>(null);

  newBranch = blankBranchForm();
  editBranch = blankBranchForm();
  sectionForm = blankSectionForm();

  ngOnInit(): void {
    this.loadBranches();
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
    this.loadSections(branch.id);
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
      this.loadBranches();
      this.selectBranch(branch);
    });
  }

  saveBranch(branch: Branch): void {
    this.run(this.api.updateBranch(branch.id, {
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
    this.run(this.api.deactivateBranch(branch.id), () => {
      this.loadBranches();
      this.selected.set({ ...branch, status: 'INACTIVE' });
    });
  }

  editSection(section: Section): void {
    this.editingSectionId.set(section.id);
    this.sectionForm = { code: section.code, name: section.name, type: section.type };
  }

  cancelSectionEdit(): void {
    this.editingSectionId.set(null);
    this.sectionForm = blankSectionForm();
  }

  submitSection(branch: Branch): void {
    const editingId = this.editingSectionId();
    if (editingId !== null) {
      this.run(this.api.updateSection(editingId, {
        name: this.sectionForm.name.trim(),
        type: this.sectionForm.type,
        managerUserId: null
      }), () => {
        this.cancelSectionEdit();
        this.loadSections(branch.id);
      });
    } else {
      this.run(this.api.createSection(branch.id, {
        code: this.sectionForm.code.trim(),
        name: this.sectionForm.name.trim(),
        type: this.sectionForm.type,
        managerUserId: null
      }), () => {
        this.cancelSectionEdit();
        this.loadSections(branch.id);
      });
    }
  }

  deactivateSection(section: Section): void {
    this.run(this.api.deactivateSection(section.id), () => this.loadSections(section.branchId));
  }

  private loadBranches(): void {
    this.api.listBranches().subscribe({
      next: branches => this.branches.set(branches),
      error: err => this.showError(err)
    });
  }

  private loadSections(branchId: number): void {
    this.api.listSections(branchId).subscribe({
      next: sections => this.sections.set(sections),
      error: err => this.showError(err)
    });
  }

  private run<T>(source: Observable<T>, onSuccess: (value: T) => void): void {
    this.saving.set(true);
    this.error.set(null);
    source.subscribe({
      next: value => {
        this.saving.set(false);
        onSuccess(value);
      },
      error: err => {
        this.saving.set(false);
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
}

function blankBranchForm() {
  return { code: '', name: '', type: 'RETAIL', phone: '', timeZone: 'Africa/Kampala', physicalAddress: '' };
}

function blankSectionForm() {
  return { code: '', name: '', type: 'RETAIL_FLOOR' as string };
}

function emptyToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

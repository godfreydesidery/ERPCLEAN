import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { CatalogService } from '../catalog.service';
import { VatGroup } from '../catalog.models';

@Component({
  selector: 'orbix-vat-group',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2 class="h3 mb-4">VAT groups</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <table class="table table-sm align-middle" style="max-width: 760px">
      <thead>
        <tr><th>Code</th><th>Name</th><th class="text-end">Rate</th><th>Valid from</th>
            <th>Default</th><th>Status</th><th></th></tr>
      </thead>
      <tbody>
        @for (group of vatGroups(); track group.id) {
          <tr>
            <td>{{ group.code }}</td>
            <td>{{ group.name }}</td>
            <td class="text-end">{{ (group.rate * 100).toFixed(2) }}%</td>
            <td>{{ group.validFrom }}</td>
            <td>{{ group.isDefault ? 'yes' : '' }}</td>
            <td>
              @if (group.status === 'ACTIVE') {
                <span class="badge text-bg-success">ACTIVE</span>
              } @else {
                <span class="badge text-bg-secondary">{{ group.status }}</span>
              }
            </td>
            <td class="text-end">
              <button class="btn btn-sm btn-outline-secondary me-1" (click)="edit(group)"
                      [disabled]="busy()">Edit</button>
              @if (group.status === 'ACTIVE') {
                <button class="btn btn-sm btn-outline-danger" (click)="archive(group)"
                        [disabled]="busy()">Archive</button>
              }
            </td>
          </tr>
        } @empty {
          <tr><td colspan="7" class="text-muted">No VAT groups yet.</td></tr>
        }
      </tbody>
    </table>

    <div class="card shadow-sm" style="max-width: 760px">
      <div class="card-header fw-semibold">{{ editingId() ? 'Edit VAT group' : 'New VAT group' }}</div>
      <div class="card-body">
        <form (ngSubmit)="submit()" #f="ngForm" class="row g-2">
          <div class="col-md-3">
            <label class="form-label">Code</label>
            <input class="form-control" name="code" [(ngModel)]="form.code" required
                   [disabled]="!!editingId()">
          </div>
          <div class="col-md-4">
            <label class="form-label">Name</label>
            <input class="form-control" name="name" [(ngModel)]="form.name" required>
          </div>
          <div class="col-md-2">
            <label class="form-label">Rate <small class="text-muted">(0–1)</small></label>
            <input class="form-control" type="number" step="0.0001" min="0" max="1"
                   name="rate" [(ngModel)]="form.rate" required>
          </div>
          <div class="col-md-3">
            <label class="form-label">Valid from</label>
            <input class="form-control" type="date" name="vf" [(ngModel)]="form.validFrom" required>
          </div>
          <div class="col-md-3 d-flex align-items-end">
            <div class="form-check">
              <input class="form-check-input" type="checkbox" id="def" name="def"
                     [(ngModel)]="form.isDefault">
              <label class="form-check-label" for="def">Company default</label>
            </div>
          </div>
          <div class="col-12 d-flex gap-2">
            <button class="btn btn-primary" [disabled]="busy() || f.invalid">
              {{ editingId() ? 'Save' : 'Create' }}
            </button>
            @if (editingId()) {
              <button type="button" class="btn btn-outline-secondary" (click)="cancel()">Cancel</button>
            }
          </div>
        </form>
      </div>
    </div>
  `
})
export class VatGroupComponent implements OnInit {
  private readonly catalog = inject(CatalogService);

  readonly vatGroups = signal<VatGroup[]>([]);
  readonly editingId = signal<number | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  form: { code: string; name: string; rate: number; validFrom: string; isDefault: boolean } = blank();

  ngOnInit(): void {
    this.load();
  }

  edit(group: VatGroup): void {
    this.editingId.set(group.id);
    this.form = {
      code: group.code,
      name: group.name,
      rate: group.rate,
      validFrom: group.validFrom,
      isDefault: group.isDefault
    };
  }

  cancel(): void {
    this.editingId.set(null);
    this.form = blank();
  }

  submit(): void {
    const id = this.editingId();
    const request = {
      name: this.form.name.trim(),
      rate: Number(this.form.rate),
      validFrom: this.form.validFrom,
      isDefault: this.form.isDefault
    };
    const source = id
      ? this.catalog.updateVatGroup(id, request)
      : this.catalog.createVatGroup({ code: this.form.code.trim(), ...request });
    this.run(source, () => {
      this.cancel();
      this.load();
    });
  }

  archive(group: VatGroup): void {
    this.run(this.catalog.archiveVatGroup(group.id), () => this.load());
  }

  private load(): void {
    this.catalog.listVatGroups().subscribe({
      next: groups => this.vatGroups.set(groups),
      error: err => this.showError(err)
    });
  }

  private run<T>(source: Observable<T>, onSuccess: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    source.subscribe({
      next: () => { this.busy.set(false); onSuccess(); },
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

function blank(): { code: string; name: string; rate: number; validFrom: string; isDefault: boolean } {
  return { code: '', name: '', rate: 0, validFrom: new Date().toISOString().slice(0, 10), isDefault: false };
}

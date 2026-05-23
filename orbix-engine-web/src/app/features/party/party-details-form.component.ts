import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PARTY_CATEGORIES, PartyDetails } from './party.models';

/**
 * The shared party-detail fields, reused by every role's create form. The
 * {@link PartyDetails} object is passed by reference and mutated in place via
 * ngModel; {@code tinBlur} fires the shared-party lookup.
 */
@Component({
  selector: 'orbix-party-details-form',
  standalone: true,
  imports: [FormsModule],
  template: `
    <fieldset class="party-fieldset">
      <legend class="party-fieldset__legend">
        <i class="bi bi-person-vcard text-secondary"></i> Party details
      </legend>
      <div class="row g-3">
        <div class="col-md-6">
          <label class="form-label small fw-semibold text-secondary">Name</label>
          <input class="form-control" name="pname" [(ngModel)]="details().name" required
                 placeholder="Legal or display name">
        </div>
        <div class="col-md-6">
          <label class="form-label small fw-semibold text-secondary">Legal name <span class="text-muted">(optional)</span></label>
          <input class="form-control" name="plegal" [ngModel]="details().legalName"
                 (ngModelChange)="details().legalName = $event || null">
        </div>
        <div class="col-md-4">
          <label class="form-label small fw-semibold text-secondary">Category</label>
          <select class="form-select" name="pcat" [(ngModel)]="details().category" required>
            @for (c of categories; track c) { <option [value]="c">{{ c }}</option> }
          </select>
        </div>
        <div class="col-md-4">
          <label class="form-label small fw-semibold text-secondary">TIN</label>
          <input class="form-control font-monospace" name="ptin" [ngModel]="details().tin"
                 (ngModelChange)="details().tin = $event || null"
                 (blur)="emitTin()" placeholder="Tax ID">
        </div>
        <div class="col-md-4">
          <label class="form-label small fw-semibold text-secondary">VRN <span class="text-muted">(optional)</span></label>
          <input class="form-control font-monospace" name="pvrn" [ngModel]="details().vrn"
                 (ngModelChange)="details().vrn = $event || null">
        </div>
        <div class="col-md-6">
          <label class="form-label small fw-semibold text-secondary">
            <i class="bi bi-telephone me-1"></i>Phone
          </label>
          <input class="form-control" name="pphone" [ngModel]="details().phone"
                 (ngModelChange)="details().phone = $event || null">
        </div>
        <div class="col-md-6">
          <label class="form-label small fw-semibold text-secondary">
            <i class="bi bi-envelope me-1"></i>Email
          </label>
          <input class="form-control" type="email" name="pemail" [ngModel]="details().email"
                 (ngModelChange)="details().email = $event || null">
        </div>
        <div class="col-md-8">
          <label class="form-label small fw-semibold text-secondary">
            <i class="bi bi-geo-alt me-1"></i>Physical address
          </label>
          <input class="form-control" name="paddr" [ngModel]="details().physicalAddress"
                 (ngModelChange)="details().physicalAddress = $event || null">
        </div>
        <div class="col-md-4">
          <label class="form-label small fw-semibold text-secondary">Country code</label>
          <input class="form-control text-uppercase font-monospace" name="pcc" maxlength="2"
                 [ngModel]="details().countryCode"
                 (ngModelChange)="details().countryCode = $event || null" placeholder="TZ">
        </div>
        <div class="col-12">
          <label class="form-label small fw-semibold text-secondary">Notes</label>
          <textarea class="form-control" rows="2" name="pnotes"
                    [ngModel]="details().notes"
                    (ngModelChange)="details().notes = $event || null"
                    placeholder="Internal notes about this party"></textarea>
        </div>
      </div>
    </fieldset>
  `,
  styles: [`
    .party-fieldset {
      background: #f9fafb;
      border: 1px solid #e5e7eb;
      border-radius: 10px;
      padding: 1rem 1.25rem 1.25rem;
    }
    .party-fieldset__legend {
      font-size: 0.78rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #374151;
      padding: 0 0.5rem;
      width: auto;
      margin-bottom: 0.5rem;
    }
    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8;
      box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }
  `]
})
export class PartyDetailsFormComponent {
  readonly details = input.required<PartyDetails>();
  readonly tinBlur = output<string>();

  readonly categories = PARTY_CATEGORIES;

  emitTin(): void {
    const tin = this.details().tin?.trim();
    if (tin) {
      this.tinBlur.emit(tin);
    }
  }
}

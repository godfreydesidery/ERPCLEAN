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
    <div class="row g-2">
      <div class="col-md-6">
        <label class="form-label">Name</label>
        <input class="form-control" name="pname" [(ngModel)]="details().name" required>
      </div>
      <div class="col-md-6">
        <label class="form-label">Legal name</label>
        <input class="form-control" name="plegal" [(ngModel)]="details().legalName">
      </div>
      <div class="col-md-4">
        <label class="form-label">Category</label>
        <select class="form-select" name="pcat" [(ngModel)]="details().category" required>
          @for (c of categories; track c) { <option [value]="c">{{ c }}</option> }
        </select>
      </div>
      <div class="col-md-4">
        <label class="form-label">TIN</label>
        <input class="form-control" name="ptin" [(ngModel)]="details().tin"
               (blur)="emitTin()">
      </div>
      <div class="col-md-4">
        <label class="form-label">VRN</label>
        <input class="form-control" name="pvrn" [(ngModel)]="details().vrn">
      </div>
      <div class="col-md-6">
        <label class="form-label">Phone</label>
        <input class="form-control" name="pphone" [(ngModel)]="details().phone">
      </div>
      <div class="col-md-6">
        <label class="form-label">Email</label>
        <input class="form-control" type="email" name="pemail" [(ngModel)]="details().email">
      </div>
      <div class="col-md-8">
        <label class="form-label">Physical address</label>
        <input class="form-control" name="paddr" [(ngModel)]="details().physicalAddress">
      </div>
      <div class="col-md-4">
        <label class="form-label">Country code</label>
        <input class="form-control" name="pcc" maxlength="2" [(ngModel)]="details().countryCode">
      </div>
      <div class="col-12">
        <label class="form-label">Notes</label>
        <textarea class="form-control" rows="2" name="pnotes" [(ngModel)]="details().notes"></textarea>
      </div>
    </div>
  `
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

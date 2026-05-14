import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'orbix-catalog',
  standalone: true,
  imports: [RouterLink],
  template: `
    <h2 class="h3 mb-4">Catalog</h2>
    <div class="row g-3">
      <div class="col-12 col-md-4">
        <a class="card shadow-sm text-decoration-none" routerLink="items">
          <div class="card-body">
            <div class="h6 mb-1">Items</div>
            <small class="text-muted">Create, edit and archive catalog items.</small>
          </div>
        </a>
      </div>
      <div class="col-12 col-md-4">
        <a class="card shadow-sm text-decoration-none" routerLink="groups">
          <div class="card-body">
            <div class="h6 mb-1">Item groups</div>
            <small class="text-muted">Maintain the item-group hierarchy.</small>
          </div>
        </a>
      </div>
      <div class="col-12 col-md-4">
        <a class="card shadow-sm text-decoration-none" routerLink="uoms">
          <div class="card-body">
            <div class="h6 mb-1">Units of measure</div>
            <small class="text-muted">Register units and their dimensions.</small>
          </div>
        </a>
      </div>
      <div class="col-12 col-md-4">
        <a class="card shadow-sm text-decoration-none" routerLink="vat-groups">
          <div class="card-body">
            <div class="h6 mb-1">VAT groups</div>
            <small class="text-muted">Tax classifications and rates.</small>
          </div>
        </a>
      </div>
      <div class="col-12 col-md-4">
        <a class="card shadow-sm text-decoration-none" routerLink="price-lists">
          <div class="card-body">
            <div class="h6 mb-1">Price lists</div>
            <small class="text-muted">Price books and price-change history.</small>
          </div>
        </a>
      </div>
    </div>
  `
})
export class CatalogComponent {}

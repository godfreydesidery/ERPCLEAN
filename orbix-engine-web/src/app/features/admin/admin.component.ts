import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HasPermissionDirective } from '../../core/auth/has-permission.directive';

@Component({
  selector: 'orbix-admin',
  standalone: true,
  imports: [RouterLink, HasPermissionDirective],
  template: `
    <h2 class="h3 mb-4">Admin</h2>
    <div class="row g-3">
      <div class="col-12 col-md-4" *orbixHasPermission="'IAM.MANAGE_ROLES'">
        <a class="card shadow-sm text-decoration-none" routerLink="roles">
          <div class="card-body">
            <div class="h6 mb-1">Roles &amp; permissions</div>
            <small class="text-muted">Create roles, assign permissions, grant to users.</small>
          </div>
        </a>
      </div>
      <div class="col-12 col-md-4" *orbixHasPermission="'ADMIN.MANAGE_BRANCHES'">
        <a class="card shadow-sm text-decoration-none" routerLink="branches">
          <div class="card-body">
            <div class="h6 mb-1">Branches &amp; sections</div>
            <small class="text-muted">Create branches, manage sections per branch.</small>
          </div>
        </a>
      </div>
      <div class="col-12 col-md-4" *orbixHasPermission="'ADMIN.MANAGE_CURRENCIES'">
        <a class="card shadow-sm text-decoration-none" routerLink="currencies">
          <div class="card-body">
            <div class="h6 mb-1">Currencies</div>
            <small class="text-muted">Register currencies, enable or disable them.</small>
          </div>
        </a>
      </div>
      <div class="col-12 col-md-4" *orbixHasPermission="'ADMIN.MANAGE_FX'">
        <a class="card shadow-sm text-decoration-none" routerLink="fx-rates">
          <div class="card-body">
            <div class="h6 mb-1">FX rates</div>
            <small class="text-muted">Quote exchange rates and view rate history.</small>
          </div>
        </a>
      </div>
    </div>
  `
})
export class AdminComponent {}

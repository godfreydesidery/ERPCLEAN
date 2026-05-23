import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiResponse } from '../../core/api/api-response';
import { AuthService } from '../../core/auth/auth.service';
import { UserAdminService } from '../admin/users/user-admin.service';

@Component({
  selector: 'orbix-change-password',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="change-pwd-shell">
      <div class="card border-0 shadow-lg change-pwd-card">
        <div class="card-body p-4 p-md-5">
          <div class="text-center mb-4">
            <div class="brand-mark mx-auto mb-3">O</div>
            <h1 class="h4 fw-semibold mb-1 brand-title">Set a new password</h1>
            <p class="text-muted small mb-0">{{ greeting() }}</p>
          </div>

          @if (error()) {
            <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
              <i class="bi bi-exclamation-triangle-fill"></i>
              <span class="flex-grow-1">{{ error() }}</span>
            </div>
          }

          <form (ngSubmit)="submit()" #f="ngForm" class="d-flex flex-column gap-3">
            <div>
              <label class="form-label small fw-semibold text-secondary">Current password</label>
              <div class="input-group">
                <span class="input-group-text"><i class="bi bi-key"></i></span>
                <input class="form-control" [type]="showCurrent() ? 'text' : 'password'"
                       name="current" [(ngModel)]="currentPassword" required>
                <button type="button" class="btn btn-outline-secondary" (click)="toggleCurrent()">
                  <i class="bi" [class.bi-eye]="!showCurrent()" [class.bi-eye-slash]="showCurrent()"></i>
                </button>
              </div>
            </div>
            <div>
              <label class="form-label small fw-semibold text-secondary">New password</label>
              <div class="input-group">
                <span class="input-group-text"><i class="bi bi-shield-lock"></i></span>
                <input class="form-control" [type]="showNew() ? 'text' : 'password'"
                       name="new" [(ngModel)]="newPassword" required minlength="10" maxlength="80">
                <button type="button" class="btn btn-outline-secondary" (click)="toggleNew()">
                  <i class="bi" [class.bi-eye]="!showNew()" [class.bi-eye-slash]="showNew()"></i>
                </button>
              </div>
              <small class="form-text text-secondary">Minimum 10 characters. Avoid common or easily guessed passwords.</small>
            </div>
            <div>
              <label class="form-label small fw-semibold text-secondary">Confirm new password</label>
              <div class="input-group">
                <span class="input-group-text"><i class="bi bi-shield-check"></i></span>
                <input class="form-control" type="password" name="confirm" [(ngModel)]="confirm" required>
              </div>
              @if (confirm && confirm !== newPassword) {
                <small class="form-text text-danger">Passwords don't match.</small>
              }
            </div>
            <button class="btn btn-primary btn-lg cta d-inline-flex justify-content-center align-items-center gap-2"
                    [disabled]="busy() || f.invalid || newPassword !== confirm">
              @if (busy()) { <span class="spinner-border spinner-border-sm"></span> }
              @else { <i class="bi bi-check2-circle"></i> }
              Save and continue
            </button>
          </form>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; min-height: 100vh; min-height: 100dvh; }

    .change-pwd-shell {
      min-height: 100vh; min-height: 100dvh;
      display: flex; align-items: center; justify-content: center;
      padding: env(safe-area-inset-top) 1rem env(safe-area-inset-bottom);
      background: linear-gradient(135deg, #0d2a5b 0%, #1a4fb5 100%);
    }
    .change-pwd-card {
      width: 100%; max-width: 480px;
      border-radius: 16px;
    }
    .brand-mark {
      width: 56px; height: 56px; border-radius: 14px;
      background: linear-gradient(135deg, #1a4fb5, #4178d9);
      color: #fff; display: flex; align-items: center; justify-content: center;
      font-weight: 700; font-size: 1.5rem;
      box-shadow: 0 4px 16px rgba(26, 79, 181, 0.3);
    }
    .brand-title { color: #0d2a5b; letter-spacing: 0.05em; }

    .form-control:focus, .input-group-text {
      border-color: #d1d5db;
    }
    .form-control:focus {
      border-color: #1d4ed8;
      box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }

    .cta {
      background: linear-gradient(135deg, #1a4fb5, #4178d9);
      border: none;
      font-weight: 600;
      letter-spacing: 0.02em;
      box-shadow: 0 4px 14px rgba(26, 79, 181, 0.3);
      transition: transform 0.15s ease, box-shadow 0.15s ease;
    }
    .cta:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: 0 6px 20px rgba(26, 79, 181, 0.4);
    }
    .cta:disabled { opacity: 0.6; }
  `]
})
export class ChangePasswordComponent {
  private readonly api = inject(UserAdminService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected currentPassword = '';
  protected newPassword = '';
  protected confirm = '';
  protected readonly showCurrent = signal(false);
  protected readonly showNew = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  greeting(): string {
    const name = this.auth.currentUser()?.displayName ?? 'there';
    const must = this.auth.currentUser()?.mustChangePassword;
    return must
      ? `Welcome, ${name}. Your administrator asked you to set a new password before continuing.`
      : `Update your password, ${name}.`;
  }

  toggleCurrent(): void { this.showCurrent.update(v => !v); }
  toggleNew(): void { this.showNew.update(v => !v); }

  submit(): void {
    if (this.newPassword !== this.confirm) {
      this.error.set("Passwords don't match");
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.changeMyPassword({
      currentPassword: this.currentPassword,
      newPassword: this.newPassword
    }).subscribe({
      next: () => {
        this.busy.set(false);
        // Clear the session so the user logs in fresh with their new password
        // and we get a clean JWT (no stale mustChangePassword flag).
        this.auth.clearSession();
        void this.router.navigate(['/login'], {
          queryParams: { message: 'Password updated — sign in to continue' }
        });
      },
      error: err => {
        this.busy.set(false);
        if (err instanceof HttpErrorResponse) {
          const envelope = err.error as ApiResponse<unknown> | null;
          const fieldMsg = envelope?.errors?.[0]?.message;
          this.error.set(fieldMsg ?? envelope?.message ?? `Request failed (${err.status})`);
        } else {
          this.error.set('Unexpected error');
        }
      }
    });
  }
}

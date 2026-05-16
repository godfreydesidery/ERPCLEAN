import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { ApiResponse } from '../../core/api/api-response';

@Component({
  selector: 'orbix-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="login-shell">
      <div class="login-card-wrap">
        <div class="card shadow-lg border-0 login-card">
          <div class="card-body p-4 p-md-5">
            <div class="text-center mb-4">
              <div class="brand-mark mx-auto mb-3">O</div>
              <h1 class="h4 fw-semibold mb-1 brand-title">ORBIX ERP</h1>
              <p class="text-muted small mb-0">Sign in to continue</p>
            </div>

            <form (ngSubmit)="submit()" #f="ngForm" autocomplete="off" novalidate>
              <div class="mb-3">
                <label class="form-label small text-muted mb-1" for="loginUsername">Username</label>
                <div class="input-group input-group-premium">
                  <span class="input-group-text"><i class="bi bi-person"></i></span>
                  <input id="loginUsername"
                         class="form-control"
                         name="u"
                         [(ngModel)]="username"
                         required
                         autocomplete="username"
                         autofocus
                         placeholder="admin">
                </div>
              </div>

              <div class="mb-3">
                <label class="form-label small text-muted mb-1 d-flex justify-content-between" for="loginPassword">
                  <span>Password</span>
                </label>
                <div class="input-group input-group-premium">
                  <span class="input-group-text"><i class="bi bi-lock"></i></span>
                  <input id="loginPassword"
                         [type]="showPassword() ? 'text' : 'password'"
                         class="form-control"
                         name="p"
                         [(ngModel)]="password"
                         required
                         autocomplete="current-password"
                         placeholder="••••••••">
                  <button type="button"
                          class="btn btn-outline-secondary"
                          tabindex="-1"
                          [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'"
                          (click)="showPassword.set(!showPassword())">
                    <i class="bi" [class.bi-eye]="!showPassword()" [class.bi-eye-slash]="showPassword()"></i>
                  </button>
                </div>
              </div>

              @if (error()) {
                <div class="alert alert-danger py-2 small mb-3" role="alert">
                  <i class="bi bi-exclamation-circle me-1"></i>{{ error() }}
                </div>
              }

              <button class="btn btn-primary w-100 fw-semibold login-cta"
                      [disabled]="loading() || f.invalid"
                      type="submit">
                @if (loading()) {
                  <span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                  Signing in…
                } @else {
                  Sign in
                  <i class="bi bi-arrow-right ms-1"></i>
                }
              </button>

              <div class="text-center mt-3">
                <a href="#" class="small text-decoration-none text-muted" (click)="forgotPassword($event)">
                  Forgot password?
                </a>
              </div>
            </form>
          </div>
        </div>

        <p class="text-center small mt-3 mb-0 login-copyright">
          &copy; {{ year }} Orbix. All rights reserved.
        </p>
      </div>
    </main>
  `,
  styles: [`
    .login-shell {
      min-height: 100vh;
      /* dvh accounts for mobile browser chrome (URL bar / safe areas) so the
         gradient fills the actual visible viewport without jumps on scroll. */
      min-height: 100dvh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1rem;
      padding-top: max(1rem, env(safe-area-inset-top));
      padding-bottom: max(1rem, env(safe-area-inset-bottom));
      background: linear-gradient(135deg, #0d2a5b 0%, #1a4fb5 55%, #4178d9 100%);
      position: relative;
      overflow: hidden;
    }
    .login-shell::before {
      content: '';
      position: absolute;
      inset: 0;
      background:
        radial-gradient(circle at 20% 20%, rgba(255, 255, 255, 0.08), transparent 40%),
        radial-gradient(circle at 80% 70%, rgba(255, 255, 255, 0.06), transparent 35%);
      pointer-events: none;
    }
    .login-card-wrap {
      width: 100%;
      max-width: 420px;
      position: relative;
      z-index: 1;
    }
    .login-card {
      border-radius: 1rem;
      backdrop-filter: blur(10px);
    }
    .brand-mark {
      width: 60px;
      height: 60px;
      border-radius: 16px;
      background: linear-gradient(135deg, #1a4fb5, #4178d9);
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.75rem;
      font-weight: 700;
      letter-spacing: -1px;
      box-shadow: 0 6px 20px rgba(26, 79, 181, 0.35);
    }
    .brand-title {
      letter-spacing: 0.12em;
      color: #1a4fb5;
    }
    .input-group-premium .input-group-text {
      background: #f8f9fb;
      border-right: 0;
      color: #6c757d;
    }
    .input-group-premium .form-control {
      border-left: 0;
      padding-left: 0;
      /* 16px on mobile prevents iOS Safari from auto-zooming on focus. */
      font-size: 1rem;
    }
    .input-group-premium .form-control:focus {
      box-shadow: none;
      border-color: #1a4fb5;
    }
    .input-group-premium:focus-within .input-group-text {
      border-color: #1a4fb5;
      color: #1a4fb5;
    }
    .input-group-premium .btn-outline-secondary {
      background: #f8f9fb;
      border-color: #ced4da;
      border-left: 0;
      color: #6c757d;
      /* Larger touch target on mobile. */
      min-width: 44px;
    }
    .input-group-premium .btn-outline-secondary:hover,
    .input-group-premium .btn-outline-secondary:focus {
      background: #eef0f3;
      color: #1a4fb5;
    }
    .login-cta {
      /* WCAG touch target: 44x44 minimum. */
      min-height: 44px;
      padding: 0.6rem 1rem;
      letter-spacing: 0.02em;
      box-shadow: 0 4px 14px rgba(26, 79, 181, 0.25);
      transition: transform 0.08s ease, box-shadow 0.15s ease;
    }
    .login-cta:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: 0 6px 18px rgba(26, 79, 181, 0.35);
    }
    .login-cta:active:not(:disabled) {
      transform: translateY(0);
    }
    .login-copyright {
      color: rgba(255, 255, 255, 0.75);
    }

    /* Tablet down — modest tightening. */
    @media (max-width: 575.98px) {
      .login-shell {
        padding: 0.75rem;
      }
      .brand-mark {
        width: 52px;
        height: 52px;
        border-radius: 14px;
        font-size: 1.5rem;
      }
      .brand-title {
        font-size: 1.125rem;
      }
    }

    /* Very short viewports (landscape phones) — top-align so the form
       isn't pushed below the keyboard / fold. */
    @media (max-height: 600px) {
      .login-shell {
        align-items: flex-start;
        padding-top: 1.5rem;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .login-cta {
        transition: none;
      }
      .login-cta:hover:not(:disabled) {
        transform: none;
      }
    }
  `]
})
export class LoginComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  username = '';
  password = '';
  loading = signal(false);
  error = signal<string | null>(null);
  showPassword = signal(false);
  readonly year = new Date().getFullYear();

  ngOnInit(): void {
    // Pre-fill username if the setup wizard handed one over via router state.
    const state = history.state as { username?: string } | null;
    if (state?.username) {
      this.username = state.username;
    }
    if (this.auth.isAuthenticated()) {
      void this.router.navigate(['/']);
    }
  }

  submit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.username, this.password).subscribe({
      next: () => this.router.navigate(['/']),
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        const envelope = err.error as ApiResponse<unknown> | null;
        if (envelope?.message) {
          this.error.set(envelope.message);
        } else if (err.status === 401) {
          this.error.set('Invalid username or password');
        } else if (err.status === 0) {
          this.error.set('Cannot reach the server — check your network and try again');
        } else {
          this.error.set('Sign-in failed (' + err.status + ')');
        }
      }
    });
  }

  forgotPassword(event: Event): void {
    event.preventDefault();
    this.error.set('Please contact your system administrator to reset your password.');
  }
}

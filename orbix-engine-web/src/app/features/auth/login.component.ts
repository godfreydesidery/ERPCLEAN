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
    <main class="d-flex justify-content-center align-items-center" style="min-height: 100vh; background: #f5f6f8;">
      <div class="card shadow-sm" style="width: 360px">
        <div class="card-body">
          <h1 class="h4 mb-3">Orbix Engine</h1>
          <p class="text-muted small mb-4">Sign in to continue</p>
          <form (ngSubmit)="submit()" #f="ngForm">
            <div class="mb-3">
              <label class="form-label">Username</label>
              <input class="form-control" name="u" [(ngModel)]="username" required autocomplete="username" autofocus>
            </div>
            <div class="mb-3">
              <label class="form-label">Password</label>
              <input type="password" class="form-control" name="p" [(ngModel)]="password" required autocomplete="current-password">
            </div>
            @if (error()) {
              <div class="alert alert-danger py-2" role="alert">{{ error() }}</div>
            }
            <button class="btn btn-primary w-100" [disabled]="loading() || f.invalid">
              {{ loading() ? 'Signing in…' : 'Sign in' }}
            </button>
          </form>
        </div>
      </div>
    </main>
  `
})
export class LoginComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  username = '';
  password = '';
  loading = signal(false);
  error = signal<string | null>(null);

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
}

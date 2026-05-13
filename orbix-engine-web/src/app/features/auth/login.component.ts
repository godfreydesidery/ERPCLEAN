import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'orbix-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="d-flex justify-content-center align-items-center" style="min-height: 100vh">
      <div class="card shadow-sm" style="width: 360px">
        <div class="card-body">
          <h1 class="h4 mb-3">Orbix Engine</h1>
          <p class="text-muted small mb-4">Sign in to continue</p>
          <form (ngSubmit)="submit()" #f="ngForm">
            <div class="mb-3">
              <label class="form-label">Username</label>
              <input class="form-control" name="u" [(ngModel)]="username" required autocomplete="username">
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
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  username = '';
  password = '';
  loading = signal(false);
  error = signal<string | null>(null);

  submit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.username, this.password).subscribe({
      next: () => this.router.navigate(['/']),
      error: () => {
        this.error.set('Invalid credentials');
        this.loading.set(false);
      }
    });
  }
}

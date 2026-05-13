import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { SetupService } from './setup.service';

type StepKey = 'organisation' | 'company' | 'branch' | 'admin';

const STEPS: { key: StepKey; title: string; hint: string }[] = [
  { key: 'organisation', title: 'Organisation', hint: 'The top-level entity that owns one or more companies.' },
  { key: 'company',      title: 'Company',      hint: 'A legal entity with its own books, VAT registration, and branches.' },
  { key: 'branch',       title: 'First branch', hint: 'A physical site. You can add more later under Admin → Branches.' },
  { key: 'admin',        title: 'Admin user',   hint: 'The first user who can log in. Add more users under Admin → Users.' }
];

@Component({
  selector: 'orbix-setup-wizard',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <main class="d-flex justify-content-center align-items-start py-5" style="min-height: 100vh; background: #f5f6f8;">
      <div class="card shadow-sm" style="width: 560px; max-width: 95vw;">
        <div class="card-body">
          <h1 class="h4 mb-1">Set up Orbix Engine</h1>
          <p class="text-muted small mb-4">Bootstrap a new deployment. This wizard runs once.</p>

          <!-- Stepper -->
          <ol class="d-flex justify-content-between list-unstyled mb-4 small">
            @for (s of steps; track s.key; let i = $index) {
              <li class="text-center" style="flex: 1">
                <div class="rounded-circle d-inline-flex align-items-center justify-content-center"
                     [class.bg-primary]="i <= stepIndex()"
                     [class.text-white]="i <= stepIndex()"
                     [class.bg-secondary-subtle]="i > stepIndex()"
                     [class.text-secondary]="i > stepIndex()"
                     style="width: 28px; height: 28px;">
                  {{ i + 1 }}
                </div>
                <div class="mt-1" [class.fw-semibold]="i === stepIndex()">{{ s.title }}</div>
              </li>
            }
          </ol>

          @if (errorMessage()) {
            <div class="alert alert-danger py-2" role="alert">{{ errorMessage() }}</div>
          }

          <form [formGroup]="form" (ngSubmit)="next()">
            <p class="text-muted small">{{ currentStep().hint }}</p>

            <!-- Step 1: Organisation -->
            <div [formGroupName]="'organisation'" [hidden]="stepIndex() !== 0">
              <div class="mb-3">
                <label class="form-label">Name *</label>
                <input class="form-control" formControlName="name" placeholder="Orbix Demo" autocomplete="organization">
              </div>
              <div class="mb-3">
                <label class="form-label">Legal name</label>
                <input class="form-control" formControlName="legalName" placeholder="Orbix Demo Ltd">
              </div>
              <div class="row g-2">
                <div class="col">
                  <label class="form-label">Currency *</label>
                  <input class="form-control text-uppercase" formControlName="currencyCode" maxlength="3" placeholder="UGX">
                  <small class="text-muted">ISO 4217 (3-letter)</small>
                </div>
                <div class="col">
                  <label class="form-label">Country *</label>
                  <input class="form-control text-uppercase" formControlName="countryCode" maxlength="2" placeholder="UG">
                  <small class="text-muted">ISO 3166-1 alpha-2</small>
                </div>
              </div>
            </div>

            <!-- Step 2: Company -->
            <div [formGroupName]="'company'" [hidden]="stepIndex() !== 1">
              <div class="mb-3">
                <label class="form-label">Code *</label>
                <input class="form-control text-uppercase" formControlName="code" maxlength="20" placeholder="DEMO">
                <small class="text-muted">Short identifier used in document numbers.</small>
              </div>
              <div class="mb-3">
                <label class="form-label">Name *</label>
                <input class="form-control" formControlName="name" placeholder="Demo Company">
              </div>
              <div class="mb-3">
                <label class="form-label">Time zone</label>
                <input class="form-control" formControlName="timeZone" placeholder="Africa/Kampala">
              </div>
            </div>

            <!-- Step 3: Branch -->
            <div [formGroupName]="'branch'" [hidden]="stepIndex() !== 2">
              <div class="mb-3">
                <label class="form-label">Code *</label>
                <input class="form-control text-uppercase" formControlName="code" maxlength="20" placeholder="HQ">
              </div>
              <div class="mb-3">
                <label class="form-label">Name *</label>
                <input class="form-control" formControlName="name" placeholder="Head Office">
              </div>
              <div class="mb-3">
                <label class="form-label">Time zone</label>
                <input class="form-control" formControlName="timeZone" placeholder="Africa/Kampala">
              </div>
            </div>

            <!-- Step 4: Admin -->
            <div [formGroupName]="'admin'" [hidden]="stepIndex() !== 3">
              <div class="mb-3">
                <label class="form-label">Display name *</label>
                <input class="form-control" formControlName="displayName" placeholder="Administrator">
              </div>
              <div class="mb-3">
                <label class="form-label">Username *</label>
                <input class="form-control" formControlName="username" autocomplete="username" placeholder="admin">
              </div>
              <div class="mb-3">
                <label class="form-label">Password *</label>
                <input type="password" class="form-control" formControlName="password" autocomplete="new-password" placeholder="At least 8 characters">
                <small class="text-muted">Minimum 8 characters.</small>
              </div>
            </div>

            <div class="d-flex justify-content-between mt-4">
              <button type="button" class="btn btn-outline-secondary" [disabled]="stepIndex() === 0 || loading()" (click)="back()">
                Back
              </button>
              @if (!isLastStep()) {
                <button type="submit" class="btn btn-primary" [disabled]="!currentStepValid() || loading()">
                  Next
                </button>
              } @else {
                <button type="submit" class="btn btn-success" [disabled]="form.invalid || loading()">
                  {{ loading() ? 'Setting up…' : 'Bootstrap deployment' }}
                </button>
              }
            </div>
          </form>
        </div>
      </div>
    </main>
  `
})
export class SetupWizardComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly setup = inject(SetupService);
  private readonly router = inject(Router);

  readonly steps = STEPS;
  readonly stepIndex = signal(0);
  readonly currentStep = computed(() => STEPS[this.stepIndex()]);
  readonly isLastStep = computed(() => this.stepIndex() === STEPS.length - 1);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form: FormGroup = this.fb.group({
    organisation: this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(200)]],
      legalName: ['', [Validators.maxLength(200)]],
      currencyCode: ['UGX', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
      countryCode: ['UG', [Validators.required, Validators.pattern(/^[A-Z]{2}$/)]]
    }),
    company: this.fb.group({
      code: ['', [Validators.required, Validators.maxLength(20)]],
      name: ['', [Validators.required, Validators.maxLength(200)]],
      timeZone: ['Africa/Kampala', [Validators.maxLength(64)]]
    }),
    branch: this.fb.group({
      code: ['HQ', [Validators.required, Validators.maxLength(20)]],
      name: ['Head Office', [Validators.required, Validators.maxLength(120)]],
      timeZone: ['Africa/Kampala', [Validators.maxLength(64)]]
    }),
    admin: this.fb.group({
      username: ['admin', [Validators.required, Validators.minLength(3), Validators.maxLength(80)]],
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(120)]],
      displayName: ['Administrator', [Validators.required, Validators.maxLength(120)]]
    })
  });

  ngOnInit(): void {
    this.setup.status().subscribe({
      next: (s) => {
        if (s.bootstrapped) {
          void this.router.navigate(['/login']);
        }
      },
      error: () => {
        // backend unreachable — let the user fill the form and discover at submit time
      }
    });
  }

  currentStepValid(): boolean {
    const group = this.form.get(this.currentStep().key) as FormGroup;
    return group ? group.valid : false;
  }

  back(): void {
    this.errorMessage.set(null);
    this.stepIndex.update(i => Math.max(0, i - 1));
  }

  next(): void {
    this.errorMessage.set(null);
    const group = this.form.get(this.currentStep().key) as FormGroup;
    if (!group.valid) {
      group.markAllAsTouched();
      return;
    }
    if (!this.isLastStep()) {
      this.stepIndex.update(i => i + 1);
      return;
    }
    this.submit();
  }

  private submit(): void {
    this.loading.set(true);
    const payload = this.form.getRawValue();
    this.setup.firstRun(payload).subscribe({
      next: (resp) => {
        void this.router.navigate(['/login'], { state: { username: resp.adminUsername } });
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        if (err.status === 409) {
          this.errorMessage.set('System is already initialised. Redirecting to login…');
          setTimeout(() => this.router.navigate(['/login']), 1500);
        } else if (err.status === 400 || err.status === 422) {
          this.errorMessage.set('Please check the form fields — some inputs were rejected.');
        } else {
          this.errorMessage.set('Setup failed: ' + (err.error?.message ?? err.message ?? 'unknown error'));
        }
      }
    });
  }
}

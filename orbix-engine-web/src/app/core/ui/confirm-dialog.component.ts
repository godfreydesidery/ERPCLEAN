import { ChangeDetectionStrategy, Component, HostListener, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmService } from './confirm.service';

/**
 * Single instance mounted at the app root. Renders the dialog described by
 * {@link ConfirmService}; nothing shows until something calls `ask(...)`.
 */
@Component({
  selector: 'orbix-confirm-dialog',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (svc.current(); as c) {
      <div class="cd-backdrop" (click)="cancel()"></div>
      <div class="cd-modal" role="dialog" aria-modal="true">
        <div class="cd-head">
          <i class="bi cd-icon"
             [class.bi-exclamation-triangle-fill]="c.variant === 'danger' || c.variant === 'warning'"
             [class.bi-question-circle-fill]="(c.variant ?? 'primary') === 'primary'"
             [class.cd-icon--danger]="c.variant === 'danger'"
             [class.cd-icon--warning]="c.variant === 'warning'"
             [class.cd-icon--primary]="(c.variant ?? 'primary') === 'primary'"></i>
          <h2 class="cd-title">{{ c.title ?? 'Please confirm' }}</h2>
        </div>
        <p class="cd-message">{{ c.message }}</p>
        @if (c.reason; as r) {
          <div class="cd-reason">
            <label class="form-label small fw-semibold text-secondary mb-1">
              {{ r.label ?? 'Reason' }}{{ r.required ? ' *' : '' }}
            </label>
            <textarea class="form-control form-control-sm" rows="2" autofocus
                      [ngModel]="reason()" (ngModelChange)="reason.set($event)"
                      [placeholder]="r.placeholder ?? ''"></textarea>
          </div>
        }
        <div class="cd-actions">
          <button type="button" class="btn btn-outline-secondary btn-sm" (click)="cancel()">
            {{ c.cancelText ?? 'Cancel' }}
          </button>
          <button type="button" class="btn btn-sm"
                  [class.btn-danger]="c.variant === 'danger'"
                  [class.btn-warning]="c.variant === 'warning'"
                  [class.btn-primary]="(c.variant ?? 'primary') === 'primary'"
                  [disabled]="confirmDisabled()"
                  (click)="confirm()">
            {{ c.confirmText ?? 'Confirm' }}
          </button>
        </div>
      </div>
    }
  `,
  styles: [`
    .cd-backdrop {
      position: fixed; inset: 0; z-index: 1090;
      background: rgba(15, 23, 42, 0.45);
    }
    .cd-modal {
      position: fixed; z-index: 1091; top: 50%; left: 50%;
      transform: translate(-50%, -50%);
      width: min(440px, calc(100vw - 2rem));
      background: #fff; border-radius: 12px; padding: 1.25rem 1.25rem 1rem;
      box-shadow: 0 20px 50px rgba(15, 23, 42, 0.25);
    }
    .cd-head { display: flex; align-items: center; gap: 0.6rem; margin-bottom: 0.5rem; }
    .cd-icon { font-size: 1.25rem; }
    .cd-icon--danger { color: #dc3545; }
    .cd-icon--warning { color: #f59e0b; }
    .cd-icon--primary { color: #1d4ed8; }
    .cd-title { font-size: 1.05rem; font-weight: 700; margin: 0; color: #111827; }
    .cd-message { font-size: 0.9rem; color: #374151; margin-bottom: 0.75rem; white-space: pre-line; }
    .cd-reason { margin-bottom: 0.75rem; }
    .cd-actions { display: flex; justify-content: flex-end; gap: 0.5rem; }
  `]
})
export class ConfirmDialogComponent {
  protected readonly svc = inject(ConfirmService);
  protected readonly reason = signal('');

  protected readonly confirmDisabled = computed(() => {
    const c = this.svc.current();
    return !!c?.reason?.required && this.reason().trim().length === 0;
  });

  protected confirm(): void {
    if (this.confirmDisabled()) return;
    this.svc.resolve(true, this.reason().trim());
    this.reason.set('');
  }

  protected cancel(): void {
    this.svc.resolve(false, '');
    this.reason.set('');
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.svc.current()) this.cancel();
  }
}

import { Injectable, signal } from '@angular/core';

/** Optional reason field shown inside the confirm dialog. */
export interface ConfirmReason {
  label?: string;
  placeholder?: string;
  required?: boolean;
}

export interface ConfirmOptions {
  title?: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  variant?: 'danger' | 'warning' | 'primary';
  /** When set, the dialog shows a reason textarea and returns its value. */
  reason?: ConfirmReason;
}

export interface ConfirmResult {
  ok: boolean;
  /** Trimmed reason text; '' when no reason field or none entered. */
  reason: string;
}

interface PendingConfirm extends ConfirmOptions {
  resolve: (result: ConfirmResult) => void;
}

/**
 * App-wide confirmation dialog. Inject and `await confirm.ask({...})`; a single
 * {@code <orbix-confirm-dialog>} mounted at the app root renders it. Replaces
 * ad-hoc native {@code window.confirm()} calls so confirmations are styled and
 * can optionally capture a reason (for audit).
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly pending = signal<PendingConfirm | null>(null);
  readonly current = this.pending.asReadonly();

  ask(options: ConfirmOptions): Promise<ConfirmResult> {
    // Auto-cancel any dialog already open so a stale promise never dangles.
    this.pending()?.resolve({ ok: false, reason: '' });
    return new Promise<ConfirmResult>(resolve => this.pending.set({ ...options, resolve }));
  }

  resolve(ok: boolean, reason: string): void {
    const p = this.pending();
    if (!p) return;
    this.pending.set(null);
    p.resolve({ ok, reason });
  }
}

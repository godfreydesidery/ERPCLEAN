import {
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { CoreLookupService, UserLookupRow } from './core-lookup.service';

export interface UserSelectedEvent {
  id: string;
  uid: string;
  displayName: string;
}

type TypeaheadState = 'idle' | 'loading' | 'results' | 'no-results';

/**
 * Remote typeahead for user lookup via GET /api/v1/users/lookup?q=.
 * Debounces at 250 ms. WCAG AA: role="combobox", aria-expanded,
 * arrow-key + Enter + Escape navigation.
 *
 * Selector: `orbix-user-picker`
 *
 * Inputs:
 *   - label (string, default "User").
 *   - ariaLabel (string) — forwarded to the inner input for SR context.
 *   - placeholder (string).
 *   - required (boolean, default false — user fields are often optional).
 *   - instanceId (string) — unique suffix for id/aria attributes.
 *   - initialUser (UserSelectedEvent | null) — pre-selects on edit.
 *   - inputTestid (string | undefined) — forwarded to input for Playwright.
 *
 * Outputs:
 *   - userSelected — emits UserSelectedEvent when user picks a result.
 *   - userCleared  — emits void when cleared.
 */
@Component({
  selector: 'orbix-user-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="user-picker position-relative" [attr.data-state]="state()">

      <label [for]="inputId" class="form-label small fw-semibold text-secondary">
        {{ label }}
        @if (required) {
          <span class="text-danger" aria-hidden="true">*</span>
        }
      </label>

      <div class="input-group">
        <input
          #inputEl
          [id]="inputId"
          type="text"
          class="form-control"
          [class.is-invalid]="touched && required && !selected()"
          autocomplete="off"
          role="combobox"
          [attr.aria-expanded]="dropdownOpen()"
          [attr.aria-controls]="listboxId"
          [attr.aria-activedescendant]="activeDescendant()"
          aria-autocomplete="list"
          aria-haspopup="listbox"
          [attr.aria-label]="ariaLabel"
          [placeholder]="placeholder"
          [(ngModel)]="query"
          (ngModelChange)="onQueryChange($event)"
          (keydown)="onKeydown($event)"
          (blur)="onBlur()"
          (focus)="onFocus()"
          [attr.required]="required || null"
          [attr.data-testid]="inputTestid ?? null"
        >
        @if (state() === 'loading') {
          <span class="input-group-text bg-white border-start-0">
            <span class="spinner-border spinner-border-sm text-secondary" aria-hidden="true"></span>
            <span class="visually-hidden">Searching users…</span>
          </span>
        }
        @if (selected()) {
          <button type="button" class="btn btn-outline-secondary border-start-0"
                  aria-label="Clear user selection"
                  (click)="clear()">
            <i class="bi bi-x-lg" aria-hidden="true"></i>
          </button>
        }
      </div>

      @if (touched && required && !selected()) {
        <div class="invalid-feedback d-block">Select a user from the list.</div>
      }

      <!-- Dropdown listbox -->
      @if (dropdownOpen()) {
        <ul
          [id]="listboxId"
          role="listbox"
          class="user-picker__dropdown list-unstyled mb-0 border rounded bg-white shadow-sm position-absolute w-100"
          style="z-index:1055; max-height:240px; overflow-y:auto; top:calc(100% + 2px);"
          (mousedown)="$event.preventDefault()"
        >
          @if (state() === 'no-results') {
            <li class="px-3 py-2 small text-secondary" role="option" aria-selected="false">
              No users found for "{{ query }}"
            </li>
          }
          @for (u of results(); track u.uid; let i = $index) {
            <li
              [id]="optionId(i)"
              role="option"
              [attr.aria-selected]="activeIndex() === i"
              class="user-picker__option px-3 py-2 small"
              [class.is-active]="activeIndex() === i"
              (click)="selectResult(u)"
              (mouseenter)="activeIndex.set(i)"
            >
              <span class="fw-semibold">{{ u.displayName }}</span>
              <span class="text-secondary ms-2 font-monospace" style="font-size:0.8rem;">
                {{ u.username }}
              </span>
            </li>
          }
        </ul>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .user-picker__dropdown { font-size: 0.875rem; }
    .user-picker__option { cursor: pointer; }
    .user-picker__option:hover,
    .user-picker__option.is-active { background: #eef4ff; color: #1d4ed8; }
    .invalid-feedback { font-size: 0.78rem; }
  `],
})
export class UserPickerComponent implements OnInit, OnChanges, OnDestroy {
  private readonly lookup = inject(CoreLookupService);

  @Input() label = 'User';
  @Input() ariaLabel = 'User';
  @Input() placeholder = 'Type to search users…';
  @Input() required = false;
  @Input() instanceId = 'default';
  @Input() initialUser: UserSelectedEvent | null = null;
  @Input() inputTestid?: string;

  @Output() readonly userSelected = new EventEmitter<UserSelectedEvent>();
  @Output() readonly userCleared = new EventEmitter<void>();

  @ViewChild('inputEl') private inputEl?: ElementRef<HTMLInputElement>;

  protected readonly state = signal<TypeaheadState>('idle');
  protected readonly results = signal<UserLookupRow[]>([]);
  protected readonly selected = signal<UserSelectedEvent | null>(null);
  protected readonly dropdownOpen = signal(false);
  protected readonly activeIndex = signal(-1);
  protected touched = false;

  protected query = '';

  get inputId(): string { return `user-picker-input-${this.instanceId}`; }
  get listboxId(): string { return `user-picker-list-${this.instanceId}`; }
  optionId(i: number): string { return `user-picker-opt-${this.instanceId}-${i}`; }

  protected activeDescendant(): string | null {
    const i = this.activeIndex();
    return i >= 0 ? this.optionId(i) : null;
  }

  private readonly search$ = new Subject<string>();
  private sub?: Subscription;

  ngOnInit(): void {
    this.sub = this.search$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap(q => {
        this.state.set('loading');
        return this.lookup.lookupUsers(q.trim(), 20);
      }),
    ).subscribe({
      next: rows => {
        this.results.set(rows);
        this.state.set(rows.length > 0 ? 'results' : 'no-results');
        this.dropdownOpen.set(true);
        this.activeIndex.set(-1);
      },
      error: () => {
        this.results.set([]);
        this.state.set('no-results');
        this.dropdownOpen.set(true);
      },
    });

    if (this.initialUser) this.applyInitial(this.initialUser);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['initialUser'] && this.initialUser && !this.selected()) {
      this.applyInitial(this.initialUser);
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  protected onQueryChange(q: string): void {
    if (this.selected()) {
      this.selected.set(null);
      this.userCleared.emit();
    }
    if (!q.trim()) {
      this.state.set('idle');
      this.dropdownOpen.set(false);
      this.results.set([]);
      return;
    }
    this.search$.next(q);
  }

  protected onFocus(): void {
    if (this.results().length > 0 && !this.selected()) {
      this.dropdownOpen.set(true);
    }
  }

  protected onBlur(): void {
    this.touched = true;
    setTimeout(() => this.dropdownOpen.set(false), 150);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const len = this.results().length;
    if (!this.dropdownOpen() || len === 0) return;

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.activeIndex.update(i => Math.min(i + 1, len - 1));
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.activeIndex.update(i => Math.max(i - 1, 0));
        break;
      case 'Enter': {
        event.preventDefault();
        const idx = this.activeIndex();
        if (idx >= 0 && idx < len) this.selectResult(this.results()[idx]);
        break;
      }
      case 'Escape':
        event.preventDefault();
        this.dropdownOpen.set(false);
        this.activeIndex.set(-1);
        break;
    }
  }

  protected selectResult(u: UserLookupRow): void {
    const evt: UserSelectedEvent = { id: u.id, uid: u.uid, displayName: u.displayName };
    this.selected.set(evt);
    this.query = u.displayName;
    this.dropdownOpen.set(false);
    this.activeIndex.set(-1);
    this.state.set('idle');
    this.userSelected.emit(evt);
  }

  protected clear(): void {
    this.selected.set(null);
    this.query = '';
    this.results.set([]);
    this.state.set('idle');
    this.dropdownOpen.set(false);
    this.touched = false;
    this.userCleared.emit();
    this.inputEl?.nativeElement.focus();
  }

  @HostListener('document:click', ['$event.target'])
  onDocumentClick(target: HTMLElement): void {
    if (!this.dropdownOpen()) return;
    const host = this.inputEl?.nativeElement.closest('.user-picker');
    if (host && !host.contains(target)) {
      this.dropdownOpen.set(false);
    }
  }

  private applyInitial(u: UserSelectedEvent): void {
    this.selected.set(u);
    this.query = u.displayName;
  }
}

import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { SearchSelectComponent } from './search-select.component';
import { CoreLookupService, SectionSummary } from './core-lookup.service';

export interface SectionSelectedEvent {
  id: string;
  uid: string;
  code: string;
  name: string;
}

/**
 * Branch-scoped section picker. Stays disabled until `branchUid` is provided.
 * Reloads whenever `branchUid` changes.
 *
 * Selector: `orbix-section-picker`
 *
 * Inputs:
 *   - branchUid (string | null) — required to fetch sections; null disables the picker.
 *   - label (string, default "Section").
 *   - required (boolean, default true).
 *   - instanceId (string).
 *   - initialSection (SectionSelectedEvent | null) — pre-selects on edit;
 *     applied after load if branchUid is already known.
 *   - disabled (boolean, default false) — extra disabled flag independent of branchUid.
 *
 * Outputs:
 *   - sectionSelected — emits SectionSelectedEvent.
 *   - sectionCleared  — emits void.
 */
@Component({
  selector: 'orbix-section-picker',
  standalone: true,
  imports: [CommonModule, FormsModule, SearchSelectComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="section-picker">
      <label [for]="selectId" class="form-label small fw-semibold text-secondary">
        {{ label }}<ng-container *ngIf="required"> <span class="text-danger" aria-hidden="true">*</span></ng-container>
      </label>

      @if (loadError()) {
        <div class="alert alert-warning py-1 px-2 small">Failed to load sections.</div>
      } @else {
        <orbix-search-select
          [id]="selectId"
          [options]="options()"
          [placeholder]="placeholderText()"
          [disabled]="isDisabled()"
          [(ngModel)]="selectedId"
          (ngModelChange)="onSelect($event)"
          [attr.aria-label]="label"
          [attr.aria-required]="required"
          [attr.aria-describedby]="touched && required && !selectedId ? errorId : null"
        ></orbix-search-select>
      }

      @if (touched && required && !selectedId) {
        <div [id]="errorId" class="text-danger" style="font-size:0.78rem; margin-top:0.25rem;">
          Select a section.
        </div>
      }
    </div>
  `,
  styles: [`:host { display: block; }`],
})
export class SectionPickerComponent implements OnChanges, OnDestroy {
  private readonly lookup = inject(CoreLookupService);

  @Input() branchUid: string | null = null;
  @Input() label = 'Section';
  @Input() required = true;
  @Input() disabled = false;
  @Input() instanceId = 'default';
  @Input() initialSection: SectionSelectedEvent | null = null;

  @Output() readonly sectionSelected = new EventEmitter<SectionSelectedEvent>();
  @Output() readonly sectionCleared = new EventEmitter<void>();

  protected readonly sections = signal<SectionSummary[]>([]);
  protected readonly loading = signal(false);
  protected readonly loadError = signal(false);

  protected readonly options = computed(() =>
    this.sections().map(s => ({ id: s.uid, label: `${s.code} — ${s.name}` }))
  );

  protected readonly isDisabled = computed(
    () => !this.branchUid || this.loading() || this.disabled
  );

  protected readonly placeholderText = computed(() => {
    if (!this.branchUid) return 'Select a branch first…';
    if (this.loading()) return 'Loading sections…';
    return 'Select section…';
  });

  protected selectedId: string | null = null;
  protected touched = false;

  get selectId(): string { return `section-picker-select-${this.instanceId}`; }
  get errorId(): string { return `section-picker-error-${this.instanceId}`; }

  private sub?: Subscription;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['branchUid']) {
      // Branch changed — clear selection and reload.
      this.selectedId = null;
      this.sections.set([]);
      this.loadError.set(false);
      if (this.branchUid) {
        this.load(this.branchUid);
      }
    }

    if (changes['initialSection'] && this.initialSection && !this.loading() && this.sections().length > 0) {
      this.applyInitial(this.initialSection);
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  protected onSelect(uid: string | number | null): void {
    this.touched = true;
    if (!uid) {
      this.selectedId = null;
      this.sectionCleared.emit();
      return;
    }
    const section = this.sections().find(s => s.uid === uid);
    if (section) {
      this.sectionSelected.emit({
        id: section.id,
        uid: section.uid,
        code: section.code,
        name: section.name,
      });
    }
  }

  private load(branchUid: string): void {
    this.loading.set(true);
    this.sub?.unsubscribe();
    this.sub = this.lookup.listSections(branchUid).subscribe({
      next: list => {
        this.sections.set(list);
        this.loading.set(false);
        if (this.initialSection) this.applyInitial(this.initialSection);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      },
    });
  }

  private applyInitial(s: SectionSelectedEvent): void {
    this.selectedId = s.uid;
  }
}

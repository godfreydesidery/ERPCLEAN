import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
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
import { CoreLookupService, BranchSummary } from './core-lookup.service';

export interface BranchSelectedEvent {
  id: string;
  uid: string;
  code: string;
  name: string;
}

/**
 * Loads all branches once on init and presents a searchable select.
 *
 * Selector: `orbix-branch-picker`
 *
 * Inputs:
 *   - label (string, default "Branch") — visible form-label text.
 *   - required (boolean, default true) — marks the field required and shows
 *     validation feedback after interaction.
 *   - initialBranch (BranchSelectedEvent | null) — pre-selects a branch on
 *     edit forms; can be set after init (ngOnChanges handles the update).
 *   - instanceId (string) — unique suffix for id/aria attributes when multiple
 *     instances appear on the same page (e.g. "from-branch" vs "to-branch").
 *   - disabled (boolean, default false).
 *
 * Outputs:
 *   - branchSelected — emits BranchSelectedEvent when user picks.
 *   - branchCleared  — emits void when the selection is removed.
 *
 * Usage:
 *   <orbix-branch-picker
 *     instanceId="from"
 *     label="From branch"
 *     [required]="true"
 *     [initialBranch]="fromBranch"
 *     (branchSelected)="onFromBranch($event)"
 *     (branchCleared)="fromBranch = null">
 *   </orbix-branch-picker>
 */
@Component({
  selector: 'orbix-branch-picker',
  standalone: true,
  imports: [CommonModule, FormsModule, SearchSelectComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="branch-picker">
      <label [for]="selectId" class="form-label small fw-semibold text-secondary">
        {{ label }}<ng-container *ngIf="required"> <span class="text-danger" aria-hidden="true">*</span></ng-container>
      </label>

      @if (loadError()) {
        <div class="alert alert-warning py-1 px-2 small">Failed to load branches.</div>
      } @else {
        <orbix-search-select
          [id]="selectId"
          [options]="options()"
          [placeholder]="loading() ? 'Loading branches…' : 'Select branch…'"
          [disabled]="loading() || disabled"
          [(ngModel)]="selectedId"
          (ngModelChange)="onSelect($event)"
          [attr.aria-label]="label"
          [attr.aria-required]="required"
          [attr.aria-describedby]="touched && required && !selectedId ? errorId : null"
        ></orbix-search-select>
      }

      @if (touched && required && !selectedId) {
        <div [id]="errorId" class="text-danger" style="font-size:0.78rem; margin-top:0.25rem;">
          Select a branch.
        </div>
      }
    </div>
  `,
  styles: [`:host { display: block; }`],
})
export class BranchPickerComponent implements OnInit, OnChanges, OnDestroy {
  private readonly lookup = inject(CoreLookupService);

  @Input() label = 'Branch';
  @Input() required = true;
  @Input() disabled = false;
  @Input() instanceId = 'default';
  @Input() initialBranch: BranchSelectedEvent | null = null;

  @Output() readonly branchSelected = new EventEmitter<BranchSelectedEvent>();
  @Output() readonly branchCleared = new EventEmitter<void>();

  protected readonly branches = signal<BranchSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);

  protected readonly options = computed(() =>
    this.branches().map(b => ({ id: b.uid, label: `${b.code} — ${b.name}` }))
  );

  protected selectedId: string | null = null;
  protected touched = false;

  get selectId(): string { return `branch-picker-select-${this.instanceId}`; }
  get errorId(): string { return `branch-picker-error-${this.instanceId}`; }

  private sub?: Subscription;

  ngOnInit(): void {
    this.sub = this.lookup.listBranches().subscribe({
      next: list => {
        this.branches.set(list);
        this.loading.set(false);
        // Apply initial after list is loaded so the label resolves.
        if (this.initialBranch) this.applyInitial(this.initialBranch);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      },
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['initialBranch'] && this.initialBranch && !this.loading()) {
      this.applyInitial(this.initialBranch);
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  protected onSelect(uid: string | number | null): void {
    this.touched = true;
    if (!uid) {
      this.selectedId = null;
      this.branchCleared.emit();
      return;
    }
    const branch = this.branches().find(b => b.uid === uid);
    if (branch) {
      this.branchSelected.emit({
        id: branch.id,
        uid: branch.uid,
        code: branch.code,
        name: branch.name,
      });
    }
  }

  private applyInitial(b: BranchSelectedEvent): void {
    this.selectedId = b.uid;
  }
}

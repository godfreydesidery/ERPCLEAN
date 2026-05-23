import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SettingsService } from './settings.service';
import { Setting, UpdateSettingItem } from './settings.models';

@Component({
  selector: 'orbix-settings',
  standalone: true,
  imports: [FormsModule],
  template: `
    <header class="mb-4">
      <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">Settings</p>
      <h1 class="h3 fw-bold mb-1 text-dark">Configuration defaults</h1>
      <p class="text-secondary mb-0 small">Override the system defaults for this deployment. Blank a field and reset to fall back to the built-in default.</p>
    </header>

    @if (info()) { <div class="alert alert-success py-2 px-3 small">{{ info() }}</div> }
    @if (error()) { <div class="alert alert-danger py-2 px-3 small">{{ error() }}</div> }

    @for (group of groups(); track group) {
      <div class="card border-0 shadow-sm mb-3">
        <div class="card-header bg-white fw-semibold small text-uppercase text-secondary" style="letter-spacing:0.06em;">{{ group }}</div>
        <div class="card-body py-2">
          @for (s of inGroup(group); track s.code) {
            <div class="row align-items-center py-2 border-bottom">
              <div class="col-12 col-md-5">
                <div class="fw-semibold small">{{ s.label }}
                  @if (s.overridden) { <span class="badge text-bg-warning ms-1">overridden</span> }
                </div>
                <div class="text-secondary" style="font-size:0.8rem;">{{ s.description }}</div>
              </div>
              <div class="col-8 col-md-5">
                @if (s.type === 'BOOLEAN') {
                  <select class="form-select form-select-sm" [(ngModel)]="draft[s.code]" [name]="s.code">
                    <option value="true">true</option>
                    <option value="false">false</option>
                  </select>
                } @else {
                  <div class="input-group input-group-sm">
                    <input class="form-control"
                           [type]="s.type === 'STRING' ? 'text' : 'number'"
                           [step]="stepFor(s.type)"
                           [(ngModel)]="draft[s.code]" [name]="s.code">
                    @if (s.type === 'PERCENT') { <span class="input-group-text">%</span> }
                    @if (s.type === 'DAYS') { <span class="input-group-text">days</span> }
                  </div>
                }
                <div class="text-secondary" style="font-size:0.75rem;">default: {{ s.defaultValue }}</div>
              </div>
              <div class="col-4 col-md-2 text-end">
                <button class="btn btn-outline-secondary btn-sm" (click)="resetOne(s)"
                        [disabled]="saving() || !s.overridden">Reset</button>
              </div>
            </div>
          }
        </div>
      </div>
    }

    <div class="d-flex gap-2">
      <button class="btn btn-primary" (click)="save()" [disabled]="saving() || !dirty()">
        {{ saving() ? 'Saving…' : 'Save changes' }}
      </button>
      <button class="btn btn-outline-secondary" (click)="reload()" [disabled]="saving()">Discard</button>
    </div>
  `
})
export class SettingsComponent implements OnInit {
  private readonly api = inject(SettingsService);

  protected readonly settings = signal<Setting[]>([]);
  protected draft: Record<string, string> = {};
  protected readonly saving = signal(false);
  protected readonly info = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly groups = computed(
    () => [...new Set(this.settings().map(s => s.group))]);

  ngOnInit(): void { this.reload(); }

  inGroup(group: string): Setting[] {
    return this.settings().filter(s => s.group === group);
  }

  stepFor(type: string): string {
    return type === 'DECIMAL' ? 'any' : (type === 'INTEGER' || type === 'DAYS' ? '1' : 'any');
  }

  dirty(): boolean {
    return this.settings().some(s => (this.draft[s.code] ?? '') !== s.value);
  }

  save(): void {
    const items: UpdateSettingItem[] = this.settings()
      .filter(s => (this.draft[s.code] ?? '') !== s.value)
      .map(s => ({ code: s.code, value: this.draft[s.code] }));
    if (items.length === 0) return;
    this.saving.set(true);
    this.error.set(null);
    this.api.update(items).subscribe({
      next: list => { this.apply(list); this.saving.set(false); this.info.set('Settings saved.'); },
      error: err => { this.saving.set(false); this.error.set(err?.error?.message ?? 'Failed to save settings.'); }
    });
  }

  resetOne(s: Setting): void {
    this.saving.set(true);
    this.error.set(null);
    this.api.update([{ code: s.code, value: null }]).subscribe({
      next: list => { this.apply(list); this.saving.set(false); this.info.set(`${s.label} reset to default.`); },
      error: err => { this.saving.set(false); this.error.set(err?.error?.message ?? 'Failed to reset.'); }
    });
  }

  reload(): void {
    this.info.set(null);
    this.error.set(null);
    this.api.list().subscribe({
      next: list => this.apply(list),
      error: () => this.error.set('Failed to load settings.')
    });
  }

  private apply(list: Setting[]): void {
    this.settings.set(list);
    this.draft = {};
    for (const s of list) {
      this.draft[s.code] = s.value;
    }
  }
}

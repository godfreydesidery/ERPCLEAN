import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { CatalogService } from '../catalog.service';
import { ItemGroup } from '../catalog.models';

@Component({
  selector: 'orbix-item-group',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <header class="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
      <div>
        <p class="text-uppercase small fw-semibold text-secondary mb-1" style="letter-spacing:0.08em;">
          <a routerLink=".." class="text-decoration-none text-secondary">Catalog</a> &rsaquo; Item groups
        </p>
        <h1 class="h3 fw-bold mb-1 text-dark">Item groups</h1>
        <p class="text-secondary mb-0 small">{{ orderedGroups().length }} group{{ orderedGroups().length === 1 ? '' : 's' }} in your hierarchy.</p>
      </div>
      <button class="btn btn-primary d-inline-flex align-items-center gap-2 shadow-sm"
              (click)="startNew()">
        <i class="bi bi-plus-lg"></i> New group
      </button>
    </header>

    @if (error()) {
      <div class="alert alert-danger d-flex align-items-center gap-2 py-2">
        <i class="bi bi-exclamation-triangle-fill"></i>
        <span class="flex-grow-1">{{ error() }}</span>
        <button type="button" class="btn-close btn-sm" aria-label="Dismiss" (click)="error.set(null)"></button>
      </div>
    }

    <div class="row g-3 g-md-4">
      <!-- Tree -->
      <div class="col-12 col-lg-6">
        <div class="card border-0 shadow-sm overflow-hidden">
          <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
            <h2 class="h6 fw-bold mb-0 text-dark">Hierarchy</h2>
            <span class="badge text-bg-light text-secondary">{{ orderedGroups().length }}</span>
          </div>
          @if (orderedGroups().length === 0) {
            <div class="p-5 text-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-diagram-3"></i></div>
              <p class="small text-secondary mb-0">No groups defined yet.</p>
            </div>
          } @else {
            <ul class="list-unstyled mb-0 tree">
              @for (group of orderedGroups(); track group.id) {
                <li>
                  <button type="button" class="tree-row"
                          [class.is-active]="selected()?.id === group.id"
                          (click)="select(group)">
                    <span class="tree-row__indent" [style.width.px]="(group.level - 1) * 20"></span>
                    @if (group.level > 1) {
                      <i class="bi bi-arrow-return-right text-secondary tree-row__branch"></i>
                    }
                    <span class="tree-row__name flex-grow-1 text-truncate">
                      <span class="fw-semibold text-dark">{{ group.name }}</span>
                      <span class="badge text-bg-light border text-secondary font-monospace ms-2">{{ group.code }}</span>
                    </span>
                    @if (group.status !== 'ACTIVE') {
                      <span class="status-badge status-badge--{{ group.status.toLowerCase() }}">
                        {{ group.status }}
                      </span>
                    }
                  </button>
                </li>
              }
            </ul>
          }
        </div>
      </div>

      <!-- Editor -->
      <div class="col-12 col-lg-6">
        @if (mode() === 'view') {
          <div class="card border-0 shadow-sm h-100">
            <div class="card-body p-5 text-center d-flex flex-column justify-content-center">
              <div class="empty-icon mx-auto mb-3"><i class="bi bi-cursor"></i></div>
              <h2 class="h6 fw-bold mb-1 text-dark">Pick a group on the left</h2>
              <p class="small text-secondary mb-3">
                Or start a new branch in the hierarchy.
              </p>
              <button class="btn btn-sm btn-outline-primary mx-auto" (click)="startNew()">
                <i class="bi bi-plus-lg me-1"></i> New group
              </button>
            </div>
          </div>
        } @else if (mode() === 'create') {
          <div class="card border-0 shadow-sm">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <h2 class="h6 fw-bold mb-0 text-dark">New group</h2>
              <button class="btn-close btn-sm" (click)="cancelEditor()" aria-label="Close"></button>
            </div>
            <div class="card-body p-3">
              <form (ngSubmit)="create()" #cf="ngForm" class="d-flex flex-column gap-3">
                <div>
                  <label class="form-label small fw-semibold text-secondary">Parent</label>
                  <select class="form-select" name="parent" [(ngModel)]="newParentId">
                    <option [ngValue]="null">(root)</option>
                    @for (g of activeGroups(); track g.id) {
                      <option [ngValue]="g.id">{{ '— '.repeat(g.level - 1) }}{{ g.name }}</option>
                    }
                  </select>
                </div>
                <div class="row g-2">
                  <div class="col-5">
                    <label class="form-label small fw-semibold text-secondary">Code</label>
                    <input class="form-control" name="code" [(ngModel)]="newCode" required placeholder="e.g. BEVS">
                  </div>
                  <div class="col-7">
                    <label class="form-label small fw-semibold text-secondary">Name</label>
                    <input class="form-control" name="name" [(ngModel)]="newName" required placeholder="e.g. Beverages">
                  </div>
                </div>
                <div class="d-flex gap-2 pt-2 border-top">
                  <button class="btn btn-primary flex-grow-1 d-inline-flex justify-content-center align-items-center gap-2"
                          [disabled]="busy() || cf.invalid">
                    @if (busy()) {
                      <span class="spinner-border spinner-border-sm"></span>
                    } @else {
                      <i class="bi bi-plus-lg"></i>
                    }
                    Create group
                  </button>
                  <button type="button" class="btn btn-outline-secondary" (click)="cancelEditor()">Cancel</button>
                </div>
              </form>
            </div>
          </div>
        } @else {
          @if (selected(); as group) {
          <div class="card border-0 shadow-sm">
            <div class="card-header bg-white border-bottom p-3 d-flex align-items-center justify-content-between">
              <div>
                <h2 class="h6 fw-bold mb-0 text-dark">{{ group.name }}</h2>
                <p class="small text-secondary mb-0">
                  <span class="font-monospace">{{ group.code }}</span> · level {{ group.level }}
                </p>
              </div>
              <span class="status-badge status-badge--{{ group.status.toLowerCase() }}">
                <span class="status-badge__dot"></span>{{ group.status }}
              </span>
            </div>
            <div class="card-body p-3 d-flex flex-column gap-3">
              <form (ngSubmit)="rename(group)" #rf="ngForm">
                <label class="form-label small fw-semibold text-secondary">Rename</label>
                <div class="input-group">
                  <input class="form-control" name="rname" [(ngModel)]="editName" required>
                  <button class="btn btn-outline-primary" [disabled]="busy() || rf.invalid">
                    <i class="bi bi-check2"></i>
                  </button>
                </div>
              </form>

              <div>
                <label class="form-label small fw-semibold text-secondary">Move under</label>
                <div class="input-group">
                  <select class="form-select" [(ngModel)]="moveParentId" name="mv">
                    <option [ngValue]="null">(root)</option>
                    @for (g of moveTargets(group); track g.id) {
                      <option [ngValue]="g.id">{{ '— '.repeat(g.level - 1) }}{{ g.name }}</option>
                    }
                  </select>
                  <button class="btn btn-outline-primary" (click)="move(group)" [disabled]="busy()">
                    <i class="bi bi-arrow-right-circle"></i>
                  </button>
                </div>
              </div>

              @if (group.status === 'ACTIVE') {
                <div class="pt-2 border-top">
                  <button class="btn btn-outline-danger w-100 d-inline-flex justify-content-center align-items-center gap-2"
                          (click)="archive(group)" [disabled]="busy()">
                    <i class="bi bi-archive"></i> Archive group
                  </button>
                </div>
              }
            </div>
          </div>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }

    .tree { max-height: 60vh; overflow-y: auto; }
    .tree-row {
      width: 100%;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.625rem 1rem;
      background: #fff;
      border: none;
      border-bottom: 1px solid #f3f4f6;
      text-align: left;
      transition: background 0.1s ease;
    }
    .tree-row:hover { background: #f8fafc; }
    .tree-row.is-active {
      background: #eef4ff;
      border-left: 3px solid #1d4ed8;
      padding-left: calc(1rem - 3px);
    }
    .tree-row__branch { font-size: 0.85rem; opacity: 0.5; }
    .tree-row__name { min-width: 0; font-size: 0.9rem; }

    .status-badge {
      display: inline-flex; align-items: center; gap: 0.375rem;
      padding: 0.25rem 0.625rem; border-radius: 999px;
      font-size: 0.72rem; font-weight: 600; letter-spacing: 0.03em;
    }
    .status-badge__dot { width: 6px; height: 6px; border-radius: 50%; }
    .status-badge--active   { background: #d1fae5; color: #047857; }
    .status-badge--active .status-badge__dot   { background: #10b981; }
    .status-badge--inactive { background: #fef3c7; color: #92400e; }
    .status-badge--inactive .status-badge__dot { background: #f59e0b; }
    .status-badge--archived { background: #f3f4f6; color: #4b5563; }
    .status-badge--archived .status-badge__dot { background: #9ca3af; }

    .empty-icon {
      width: 64px; height: 64px; border-radius: 16px;
      background: #ede9fe; color: #6d28d9; font-size: 1.75rem;
      display: flex; align-items: center; justify-content: center;
    }

    .form-control:focus, .form-select:focus {
      border-color: #1d4ed8;
      box-shadow: 0 0 0 0.2rem rgba(29, 78, 216, 0.12);
    }
  `]
})
export class ItemGroupComponent implements OnInit {
  private readonly catalog = inject(CatalogService);

  protected readonly groups = signal<ItemGroup[]>([]);
  protected readonly selected = signal<ItemGroup | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly mode = signal<'view' | 'create' | 'edit'>('view');

  protected newParentId: number | null = null;
  protected newCode = '';
  protected newName = '';

  protected editName = '';
  protected moveParentId: number | null = null;

  protected readonly orderedGroups = computed(() =>
    [...this.groups()].sort((a, b) => a.level - b.level || a.code.localeCompare(b.code))
  );
  protected readonly activeGroups = computed(() => this.orderedGroups().filter(g => g.status === 'ACTIVE'));

  ngOnInit(): void {
    this.load();
  }

  moveTargets(group: ItemGroup): ItemGroup[] {
    const descendants = this.descendantIds(group.id);
    return this.activeGroups().filter(g => g.id !== group.id && !descendants.has(g.id));
  }

  select(group: ItemGroup): void {
    this.selected.set(group);
    this.editName = group.name;
    this.moveParentId = group.parentId;
    this.mode.set('edit');
  }

  startNew(): void {
    this.mode.set('create');
    this.selected.set(null);
    this.newParentId = null;
    this.newCode = '';
    this.newName = '';
  }

  cancelEditor(): void {
    this.mode.set('view');
    this.selected.set(null);
  }

  create(): void {
    this.run(this.catalog.createGroup({
      parentId: this.newParentId,
      code: this.newCode.trim(),
      name: this.newName.trim()
    }), () => {
      this.cancelEditor();
      this.load();
    });
  }

  rename(group: ItemGroup): void {
    this.run(this.catalog.renameGroup(group.id, this.editName.trim()), updated => {
      this.load();
      this.select(updated);
    });
  }

  move(group: ItemGroup): void {
    this.run(this.catalog.moveGroup(group.id, this.moveParentId), updated => {
      this.load();
      this.select(updated);
    });
  }

  archive(group: ItemGroup): void {
    this.run(this.catalog.archiveGroup(group.id), () => {
      this.cancelEditor();
      this.load();
    });
  }

  private descendantIds(rootId: number): Set<number> {
    const childrenByParent = new Map<number, ItemGroup[]>();
    for (const g of this.groups()) {
      if (g.parentId !== null) {
        const list = childrenByParent.get(g.parentId) ?? [];
        list.push(g);
        childrenByParent.set(g.parentId, list);
      }
    }
    const result = new Set<number>();
    const queue = [rootId];
    while (queue.length > 0) {
      const current = queue.shift()!;
      for (const child of childrenByParent.get(current) ?? []) {
        if (!result.has(child.id)) {
          result.add(child.id);
          queue.push(child.id);
        }
      }
    }
    return result;
  }

  private load(): void {
    this.catalog.listGroups().subscribe({
      next: groups => this.groups.set(groups),
      error: err => this.showError(err)
    });
  }

  private run<T>(source: Observable<T>, onSuccess: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    source.subscribe({
      next: value => { this.busy.set(false); onSuccess(value); },
      error: err => { this.busy.set(false); this.showError(err); }
    });
  }

  private showError(err: unknown): void {
    if (err instanceof HttpErrorResponse) {
      const envelope = err.error as ApiResponse<unknown> | null;
      this.error.set(envelope?.message ?? `Request failed (${err.status})`);
    } else {
      this.error.set('Unexpected error');
    }
  }
}

import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../../../core/api/api-response';
import { CatalogService } from '../catalog.service';
import { ItemGroup } from '../catalog.models';

@Component({
  selector: 'orbix-item-group',
  standalone: true,
  imports: [FormsModule],
  template: `
    <h2 class="h3 mb-4">Item groups</h2>

    @if (error()) {
      <div class="alert alert-danger py-2">{{ error() }}</div>
    }

    <div class="row g-4">
      <!-- Tree + create -->
      <div class="col-12 col-lg-5">
        <div class="card shadow-sm">
          <div class="card-header fw-semibold">Hierarchy</div>
          <div class="list-group list-group-flush">
            @for (group of orderedGroups(); track group.id) {
              <button type="button"
                      class="list-group-item list-group-item-action d-flex justify-content-between"
                      [class.active]="selected()?.id === group.id"
                      (click)="select(group)">
                <span [style.padding-left.px]="(group.level - 1) * 16">
                  {{ group.name }} <small class="text-muted">{{ group.code }}</small>
                </span>
                @if (group.status !== 'ACTIVE') {
                  <span class="badge text-bg-secondary">{{ group.status }}</span>
                }
              </button>
            } @empty {
              <div class="list-group-item text-muted">No groups yet.</div>
            }
          </div>
        </div>

        <div class="card shadow-sm mt-3">
          <div class="card-header fw-semibold">New group</div>
          <div class="card-body">
            <form (ngSubmit)="create()" #cf="ngForm">
              <div class="mb-2">
                <label class="form-label">Parent</label>
                <select class="form-select" name="parent" [(ngModel)]="newParentId">
                  <option [ngValue]="null">(root)</option>
                  @for (g of activeGroups(); track g.id) {
                    <option [ngValue]="g.id">{{ '— '.repeat(g.level - 1) }}{{ g.name }}</option>
                  }
                </select>
              </div>
              <div class="mb-2">
                <label class="form-label">Code</label>
                <input class="form-control" name="code" [(ngModel)]="newCode" required>
              </div>
              <div class="mb-2">
                <label class="form-label">Name</label>
                <input class="form-control" name="name" [(ngModel)]="newName" required>
              </div>
              <button class="btn btn-primary w-100" [disabled]="busy() || cf.invalid">Create group</button>
            </form>
          </div>
        </div>
      </div>

      <!-- Selected group -->
      <div class="col-12 col-lg-7">
        @if (selected(); as group) {
          <div class="card shadow-sm">
            <div class="card-header d-flex justify-content-between align-items-center">
              <span class="fw-semibold">{{ group.code }} · level {{ group.level }}</span>
              @if (group.status === 'ACTIVE') {
                <button class="btn btn-sm btn-outline-danger" (click)="archive(group)"
                        [disabled]="busy()">Archive</button>
              } @else {
                <span class="badge text-bg-secondary">{{ group.status }}</span>
              }
            </div>
            <div class="card-body">
              <form (ngSubmit)="rename(group)" #rf="ngForm" class="mb-4">
                <label class="form-label">Name</label>
                <div class="input-group">
                  <input class="form-control" name="rname" [(ngModel)]="editName" required>
                  <button class="btn btn-outline-primary" [disabled]="busy() || rf.invalid">Rename</button>
                </div>
              </form>

              <label class="form-label">Move under</label>
              <div class="input-group">
                <select class="form-select" [(ngModel)]="moveParentId">
                  <option [ngValue]="null">(root)</option>
                  @for (g of moveTargets(group); track g.id) {
                    <option [ngValue]="g.id">{{ '— '.repeat(g.level - 1) }}{{ g.name }}</option>
                  }
                </select>
                <button class="btn btn-outline-primary" (click)="move(group)" [disabled]="busy()">
                  Move
                </button>
              </div>
            </div>
          </div>
        } @else {
          <div class="text-muted">Select a group to rename, move or archive it.</div>
        }
      </div>
    </div>
  `
})
export class ItemGroupComponent implements OnInit {
  private readonly catalog = inject(CatalogService);

  readonly groups = signal<ItemGroup[]>([]);
  readonly selected = signal<ItemGroup | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  newParentId: number | null = null;
  newCode = '';
  newName = '';

  editName = '';
  moveParentId: number | null = null;

  readonly orderedGroups = computed(() =>
    [...this.groups()].sort((a, b) => a.level - b.level || a.code.localeCompare(b.code))
  );
  readonly activeGroups = computed(() => this.orderedGroups().filter(g => g.status === 'ACTIVE'));

  ngOnInit(): void {
    this.load();
  }

  /** Valid move targets exclude the group itself and its descendants. */
  moveTargets(group: ItemGroup): ItemGroup[] {
    const descendants = this.descendantIds(group.id);
    return this.activeGroups().filter(g => g.id !== group.id && !descendants.has(g.id));
  }

  select(group: ItemGroup): void {
    this.selected.set(group);
    this.editName = group.name;
    this.moveParentId = group.parentId;
  }

  create(): void {
    this.run(this.catalog.createGroup({
      parentId: this.newParentId,
      code: this.newCode.trim(),
      name: this.newName.trim()
    }), () => {
      this.newParentId = null;
      this.newCode = '';
      this.newName = '';
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
      this.selected.set(null);
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

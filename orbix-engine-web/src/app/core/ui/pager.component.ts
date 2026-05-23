import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

/**
 * Reusable page navigation for server-side-paginated lists. Renders a
 * Bootstrap pagination strip — first/prev, a windowed run of numbered pages
 * with ellipsis gaps, then next/last — plus a "x–y of N" range summary.
 *
 * Page numbers are zero-based on the wire (matching Spring `Page`); the UI
 * shows them one-based. Emits {@code pageChange} with the zero-based target.
 *
 * Usage:
 *   <orbix-pager [page]="page()" [totalPages]="totalPages()"
 *                [totalElements]="total()" [pageSize]="size"
 *                (pageChange)="goTo($event)"/>
 */
@Component({
  selector: 'orbix-pager',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (totalPages() > 1) {
      <nav class="pager d-flex flex-wrap align-items-center justify-content-between gap-2"
           aria-label="Pagination">
        <span class="small text-secondary">{{ rangeLabel() }}</span>
        <ul class="pagination pagination-sm mb-0">
          <li class="page-item" [class.disabled]="page() === 0">
            <button type="button" class="page-link" aria-label="First page"
                    [disabled]="page() === 0" (click)="go(0)">
              <i class="bi bi-chevron-double-left"></i>
            </button>
          </li>
          <li class="page-item" [class.disabled]="page() === 0">
            <button type="button" class="page-link" aria-label="Previous page"
                    [disabled]="page() === 0" (click)="go(page() - 1)">
              <i class="bi bi-chevron-left"></i>
            </button>
          </li>
          @for (p of pages(); track $index) {
            @if (p < 0) {
              <li class="page-item disabled"><span class="page-link">…</span></li>
            } @else {
              <li class="page-item" [class.active]="p === page()">
                <button type="button" class="page-link"
                        [attr.aria-current]="p === page() ? 'page' : null"
                        (click)="go(p)">{{ p + 1 }}</button>
              </li>
            }
          }
          <li class="page-item" [class.disabled]="page() + 1 >= totalPages()">
            <button type="button" class="page-link" aria-label="Next page"
                    [disabled]="page() + 1 >= totalPages()" (click)="go(page() + 1)">
              <i class="bi bi-chevron-right"></i>
            </button>
          </li>
          <li class="page-item" [class.disabled]="page() + 1 >= totalPages()">
            <button type="button" class="page-link" aria-label="Last page"
                    [disabled]="page() + 1 >= totalPages()" (click)="go(totalPages() - 1)">
              <i class="bi bi-chevron-double-right"></i>
            </button>
          </li>
        </ul>
      </nav>
    }
  `,
  styles: [`
    :host { display: block; }
    .pager { padding: 0.25rem 0; }
    .page-link { cursor: pointer; min-width: 2rem; text-align: center; }
    .page-item.active .page-link { background-color: #0d2a5b; border-color: #0d2a5b; }
  `]
})
export class PagerComponent {
  /** Current page, zero-based. */
  readonly page = input.required<number>();
  /** Total number of pages. */
  readonly totalPages = input.required<number>();
  /** Total element count, for the range label. Optional. */
  readonly totalElements = input<number | null>(null);
  /** Page size, for the range label. Optional. */
  readonly pageSize = input<number | null>(null);

  /** Emits the zero-based page the user requested. */
  readonly pageChange = output<number>();

  /**
   * The windowed page numbers to render: always first and last, the current
   * page ±1, with -1 sentinels marking ellipsis gaps. Keeps the strip to a
   * fixed width no matter how many pages exist.
   */
  protected readonly pages = computed<number[]>(() => {
    const total = this.totalPages();
    const current = this.page();
    if (total <= 7) {
      return Array.from({ length: total }, (_, i) => i);
    }
    const out: number[] = [0];
    const start = Math.max(1, current - 1);
    const end = Math.min(total - 2, current + 1);
    if (start > 1) out.push(-1);
    for (let i = start; i <= end; i++) out.push(i);
    if (end < total - 2) out.push(-1);
    out.push(total - 1);
    return out;
  });

  protected readonly rangeLabel = computed<string>(() => {
    const total = this.totalElements();
    if (total == null) return `Page ${this.page() + 1} of ${this.totalPages()}`;
    const size = this.pageSize();
    if (size == null) return `${total} total · page ${this.page() + 1} of ${this.totalPages()}`;
    const from = this.page() * size + 1;
    const to = Math.min(total, (this.page() + 1) * size);
    return `${from}–${to} of ${total}`;
  });

  protected go(p: number): void {
    if (p < 0 || p >= this.totalPages() || p === this.page()) return;
    this.pageChange.emit(p);
  }
}

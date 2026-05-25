import { Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { CatalogService } from '../catalog.service';
import { PriceChangeLog } from '../catalog.models';

@Component({
  selector: 'orbix-price-history-panel',
  standalone: true,
  imports: [DatePipe],
  template: `
    <h3 class="h6 mt-4">Price history</h3>
    <table class="table table-sm align-middle" style="max-width: 720px">
      <thead>
        <tr><th>Effective from</th><th class="text-end">Old</th><th class="text-end">New</th>
            <th>Reason</th><th>Changed at</th></tr>
      </thead>
      <tbody>
        @for (change of changes(); track change.id) {
          <tr>
            <td>{{ change.effectiveFrom | date:'mediumDate' }}</td>
            <td class="text-end">{{ change.oldPrice ?? '—' }}</td>
            <td class="text-end">
              @if (change.newPrice === null) {
                <span class="badge text-bg-light text-secondary">discontinued</span>
              } @else {
                {{ change.newPrice }}
              }
            </td>
            <td>{{ change.reason ?? '' }}</td>
            <td>{{ change.changedAt | date:'short' }}</td>
          </tr>
        } @empty {
          <tr><td colspan="5" class="text-muted">No price changes recorded.</td></tr>
        }
      </tbody>
    </table>
  `
})
export class PriceHistoryPanelComponent implements OnInit {
  private readonly catalog = inject(CatalogService);

  readonly itemUid = input.required<string>();
  readonly changes = signal<PriceChangeLog[]>([]);

  ngOnInit(): void {
    this.catalog.priceHistory(this.itemUid()).subscribe({
      next: changes => this.changes.set(changes),
      error: () => this.changes.set([])
    });
  }
}

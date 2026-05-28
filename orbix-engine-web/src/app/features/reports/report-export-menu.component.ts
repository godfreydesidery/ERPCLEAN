import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { ReportExport, ReportExportService, RowCapExceededError } from './report-export.service';

export type ExportFormat = 'pdf' | 'excel' | 'csv';

@Component({
  selector: 'orbix-report-export-menu',
  standalone: true,
  imports: [NgClass],
  template: `
    <div class="btn-group" role="group" aria-label="Export report">
      <!-- PDF -->
      <button
        type="button"
        class="btn btn-outline-secondary btn-sm"
        [class.disabled]="disabled || busy() === 'pdf'"
        [attr.aria-disabled]="disabled || busy() === 'pdf'"
        [attr.aria-busy]="busy() === 'pdf'"
        data-testid="export-pdf"
        (click)="onExport('pdf')"
        title="Export as PDF">
        @if (busy() === 'pdf') {
          <span class="spinner-border spinner-border-sm me-1" role="status"
                aria-hidden="true"></span>
        } @else {
          <i class="bi bi-file-earmark-pdf me-1" aria-hidden="true"></i>
        }
        PDF
      </button>

      <!-- Excel -->
      <button
        type="button"
        class="btn btn-outline-secondary btn-sm"
        [class.disabled]="disabled || busy() === 'excel'"
        [attr.aria-disabled]="disabled || busy() === 'excel'"
        [attr.aria-busy]="busy() === 'excel'"
        data-testid="export-excel"
        (click)="onExport('excel')"
        title="Export as Excel">
        @if (busy() === 'excel') {
          <span class="spinner-border spinner-border-sm me-1" role="status"
                aria-hidden="true"></span>
        } @else {
          <i class="bi bi-file-earmark-spreadsheet me-1" aria-hidden="true"></i>
        }
        Excel
      </button>

      <!-- CSV -->
      <button
        type="button"
        class="btn btn-outline-secondary btn-sm"
        [class.disabled]="disabled || busy() === 'csv'"
        [attr.aria-disabled]="disabled || busy() === 'csv'"
        [attr.aria-busy]="busy() === 'csv'"
        data-testid="export-csv"
        (click)="onExport('csv')"
        title="Export as CSV">
        @if (busy() === 'csv') {
          <span class="spinner-border spinner-border-sm me-1" role="status"
                aria-hidden="true"></span>
        } @else {
          <i class="bi bi-filetype-csv me-1" aria-hidden="true"></i>
        }
        CSV
      </button>
    </div>

    @if (errorMessage()) {
      <div class="alert alert-warning alert-sm py-1 px-2 mt-2 small" role="alert" aria-live="polite">
        <i class="bi bi-exclamation-triangle-fill me-1" aria-hidden="true"></i>
        {{ errorMessage() }}
      </div>
    }
  `,
  styles: [`:host { display: inline-block; }`],
})
export class ReportExportMenuComponent {
  /**
   * Called at click-time to build the export payload. Returning null/undefined
   * will be treated as an empty export (buttons disabled by `disabled` input).
   */
  @Input({ required: true }) exportBuilder!: () => ReportExport;

  /**
   * When true all three buttons are visually disabled and no export fires.
   * Callers should set this when there are no rows to export.
   */
  @Input() disabled = false;

  @Output() readonly exportStarted = new EventEmitter<ExportFormat>();
  @Output() readonly exportComplete = new EventEmitter<ExportFormat>();
  @Output() readonly exportFailed = new EventEmitter<{ format: ExportFormat; error: unknown }>();

  protected readonly busy = signal<ExportFormat | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  constructor(private readonly exportService: ReportExportService) {}

  async onExport(format: ExportFormat): Promise<void> {
    if (this.disabled || this.busy() !== null) return;

    this.errorMessage.set(null);
    this.busy.set(format);
    this.exportStarted.emit(format);

    try {
      const data = this.exportBuilder();

      if (format === 'csv') {
        this.exportService.exportCsv(data);
      } else if (format === 'excel') {
        await this.exportService.exportExcel(data);
      } else {
        await this.exportService.exportPdf(data);
      }

      this.exportComplete.emit(format);
    } catch (err) {
      this.exportFailed.emit({ format, error: err });
      if (err instanceof RowCapExceededError) {
        this.errorMessage.set(err.message);
      } else {
        this.errorMessage.set(`Export failed. Please try again.`);
      }
    } finally {
      this.busy.set(null);
    }
  }
}

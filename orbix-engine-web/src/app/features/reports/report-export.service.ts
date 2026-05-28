import { Injectable } from '@angular/core';

// ---------------------------------------------------------------------------
// Public types
// ---------------------------------------------------------------------------

export interface ReportExportColumn {
  key: string;
  label: string;
  align?: 'left' | 'right' | 'center';
  format?: 'currency' | 'date' | 'number' | 'text';
}

export interface ReportExport {
  title: string;
  subtitle?: string;
  columns: ReportExportColumn[];
  rows: Array<Record<string, unknown>>;
  totals?: Record<string, number | string>;
}

// ---------------------------------------------------------------------------
// Error thrown when the row cap is exceeded
// ---------------------------------------------------------------------------

export class RowCapExceededError extends Error {
  constructor(rowCount: number) {
    super(
      `Export contains ${rowCount.toLocaleString()} rows which exceeds the ` +
        `5,000-row in-browser limit. Use a tighter date range, or wait for the ` +
        `scheduled-export feature to become available.`
    );
    this.name = 'RowCapExceededError';
  }
}

// ---------------------------------------------------------------------------
// Currency / value formatters (TZS, Tanzania locale)
// ---------------------------------------------------------------------------

const TZS_FORMATTER = (() => {
  try {
    // Verify sw-TZ produces a recognisable TZS result
    const sample = new Intl.NumberFormat('sw-TZ', {
      style: 'currency',
      currency: 'TZS',
      maximumFractionDigits: 0,
    }).format(1234);
    // If the output doesn't contain 1,234 we fall back
    if (!sample.includes('1,234')) throw new Error('sw-TZ fallback');
    return new Intl.NumberFormat('sw-TZ', {
      style: 'currency',
      currency: 'TZS',
      maximumFractionDigits: 0,
    });
  } catch {
    try {
      return new Intl.NumberFormat('en-TZ', {
        style: 'currency',
        currency: 'TZS',
        maximumFractionDigits: 0,
      });
    } catch {
      return null;
    }
  }
})();

function formatCurrency(value: number): string {
  if (TZS_FORMATTER) return TZS_FORMATTER.format(value);
  // Hand-rolled fallback: "TZS 1,234"
  const rounded = Math.round(value);
  return `TZS ${rounded.toLocaleString('en-US')}`;
}

function formatValue(value: unknown, fmt?: ReportExportColumn['format']): string {
  if (value === null || value === undefined) return '';
  switch (fmt) {
    case 'currency':
      return formatCurrency(Number(value));
    case 'number':
      return Number(value).toLocaleString('en-US');
    case 'date':
      return String(value); // already ISO strings from the backend
    default:
      return String(value);
  }
}

// ---------------------------------------------------------------------------
// Filename helper
// ---------------------------------------------------------------------------

function buildFilename(title: string, ref: string | undefined, ext: string): string {
  const slug = title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  const dateRef = ref ?? new Date().toISOString().slice(0, 10);
  const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  return `${slug}_${dateRef}_${ts}.${ext}`;
}

function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.style.display = 'none';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  // Revoke after a brief delay to let the browser start the download
  setTimeout(() => URL.revokeObjectURL(url), 5000);
}

// ---------------------------------------------------------------------------
// Row cap guard
// ---------------------------------------------------------------------------

const ROW_CAP = 5000;

function assertRowCap(rows: unknown[]): void {
  if (rows.length > ROW_CAP) throw new RowCapExceededError(rows.length);
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

@Injectable({ providedIn: 'root' })
export class ReportExportService {

  // -------------------------------------------------------------------------
  // CSV — pure TS, no external deps
  // -------------------------------------------------------------------------
  exportCsv(data: ReportExport): void {
    assertRowCap(data.rows);

    const escape = (val: string): string => {
      // RFC 4180: wrap in quotes if contains comma, quote, or newline
      if (/[",\r\n]/.test(val)) return `"${val.replace(/"/g, '""')}"`;
      return val;
    };

    const lines: string[] = [];

    // Header row
    lines.push(data.columns.map(c => escape(c.label)).join(','));

    // Data rows
    for (const row of data.rows) {
      lines.push(
        data.columns
          .map(col => escape(formatValue(row[col.key], col.format)))
          .join(',')
      );
    }

    // Totals row
    if (data.totals) {
      const totalsRow = data.columns
        .map(col => {
          const v = data.totals![col.key];
          return v !== undefined ? escape(formatValue(v, col.format)) : '';
        })
        .join(',');
      lines.push(totalsRow);
    }

    // UTF-8 BOM so Excel opens without garbled characters
    const bom = '﻿';
    const csvText = bom + lines.join('\r\n');

    const blob = new Blob([csvText], { type: 'text/csv;charset=utf-8' });
    triggerDownload(blob, buildFilename(data.title, undefined, 'csv'));
  }

  // -------------------------------------------------------------------------
  // Excel — lazy-loads xlsx
  // -------------------------------------------------------------------------
  async exportExcel(data: ReportExport): Promise<void> {
    assertRowCap(data.rows);

    const XLSX = await import('xlsx');

    // Build array-of-arrays: header + rows + optional totals
    const header = data.columns.map(c => c.label);
    const body = data.rows.map(row =>
      data.columns.map(col => {
        const raw = row[col.key];
        if (col.format === 'currency' || col.format === 'number') return Number(raw ?? 0);
        return raw === null || raw === undefined ? '' : String(raw);
      })
    );

    const aoa: unknown[][] = [header, ...body];

    if (data.totals) {
      aoa.push(
        data.columns.map(col => {
          const v = data.totals![col.key];
          if (v === undefined) return '';
          if (col.format === 'currency' || col.format === 'number') return Number(v);
          return String(v);
        })
      );
    }

    const ws = XLSX.utils.aoa_to_sheet(aoa);
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, data.title.slice(0, 31));

    // Subtitle in sheet properties
    if (data.subtitle) {
      wb.Props = { ...wb.Props, Subject: data.subtitle };
    }

    XLSX.writeFile(wb, buildFilename(data.title, undefined, 'xlsx'));
  }

  // -------------------------------------------------------------------------
  // PDF — lazy-loads jspdf + jspdf-autotable
  // -------------------------------------------------------------------------
  async exportPdf(data: ReportExport): Promise<void> {
    assertRowCap(data.rows);

    const [{ jsPDF }, { default: autoTable }] = await Promise.all([
      import('jspdf'),
      import('jspdf-autotable'),
    ]);

    const landscape = data.columns.length > 6;
    const doc = new jsPDF({
      orientation: landscape ? 'landscape' : 'portrait',
      unit: 'mm',
      format: 'a4',
    });

    // Title
    doc.setFontSize(14);
    doc.setFont('helvetica', 'bold');
    doc.text(data.title, 14, 15);

    // Subtitle
    if (data.subtitle) {
      doc.setFontSize(9);
      doc.setFont('helvetica', 'normal');
      doc.setTextColor(100);
      doc.text(data.subtitle, 14, 22);
      doc.setTextColor(0);
    }

    const startY = data.subtitle ? 27 : 20;

    const head = [data.columns.map(c => c.label)];
    const body = data.rows.map(row =>
      data.columns.map(col => formatValue(row[col.key], col.format))
    );

    const foot: string[][] = [];
    if (data.totals) {
      foot.push(
        data.columns.map(col => {
          const v = data.totals![col.key];
          return v !== undefined ? formatValue(v, col.format) : '';
        })
      );
    }

    autoTable(doc, {
      head,
      body,
      foot,
      startY,
      styles: { fontSize: 8, cellPadding: 2 },
      headStyles: { fillColor: [29, 78, 216], textColor: 255, fontStyle: 'bold' },
      footStyles: { fillColor: [243, 244, 246], fontStyle: 'bold' },
      columnStyles: Object.fromEntries(
        data.columns.map((col, i) => [
          i,
          { halign: col.align ?? (col.format === 'currency' || col.format === 'number' ? 'right' : 'left') },
        ])
      ),
    });

    doc.save(buildFilename(data.title, undefined, 'pdf'));
  }
}

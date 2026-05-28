import { TestBed } from '@angular/core/testing';
import {
  ReportExport,
  ReportExportColumn,
  ReportExportService,
  RowCapExceededError,
} from './report-export.service';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeExport(overrides: Partial<ReportExport> = {}): ReportExport {
  return {
    title: 'Test Report',
    subtitle: 'Branch: HQ · 2026-05-28',
    columns: [
      { key: 'name',   label: 'Name',   align: 'left',  format: 'text' },
      { key: 'amount', label: 'Amount', align: 'right', format: 'currency' },
      { key: 'qty',    label: 'Qty',    align: 'right', format: 'number' },
    ] as ReportExportColumn[],
    rows: [
      { name: 'Item A', amount: 1500,  qty: 3 },
      { name: 'Item B', amount: 75000, qty: 10 },
    ],
    totals: { name: 'Total', amount: 76500, qty: 13 },
    ...overrides,
  };
}

/**
 * Returns the list of Blobs that were passed to URL.createObjectURL during
 * the test. The outer beforeEach already spies on URL.createObjectURL, so we
 * simply read its call arguments — no Blob constructor replacement needed.
 * Must be called AFTER service.exportCsv() so the calls are recorded.
 */
function capturedBlobs(): Blob[] {
  const spy = URL.createObjectURL as jasmine.Spy;
  return spy.calls.all().map(c => c.args[0] as Blob).filter(a => a instanceof Blob);
}

// ---------------------------------------------------------------------------
// ReportExportService
// ---------------------------------------------------------------------------

describe('ReportExportService', () => {
  let service: ReportExportService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ReportExportService);

    spyOn(URL, 'createObjectURL').and.returnValue('blob:test/fake');
    spyOn(URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click');
  });

  // -------------------------------------------------------------------------
  // RowCapExceededError
  // -------------------------------------------------------------------------
  describe('RowCapExceededError', () => {
    it('is an instance of Error', () => {
      expect(new RowCapExceededError(9999) instanceof Error).toBeTrue();
    });

    it('has name RowCapExceededError', () => {
      expect(new RowCapExceededError(1).name).toBe('RowCapExceededError');
    });

    it('message contains the row count formatted with commas', () => {
      expect(new RowCapExceededError(10000).message).toContain('10,000');
    });
  });

  // -------------------------------------------------------------------------
  // CSV
  // -------------------------------------------------------------------------
  describe('exportCsv()', () => {
    it('triggers a download (anchor click)', () => {
      service.exportCsv(makeExport());
      expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledTimes(1);
    });

    it('calls URL.createObjectURL with a Blob', () => {
      service.exportCsv(makeExport());
      const args = (URL.createObjectURL as jasmine.Spy).calls.mostRecent().args;
      expect(args[0] instanceof Blob).toBeTrue();
    });

    it('throws RowCapExceededError for > 5000 rows', () => {
      const bigRows = Array.from({ length: 5001 }, (_, i) => ({ name: `R${i}`, amount: i, qty: 1 }));
      expect(() => service.exportCsv(makeExport({ rows: bigRows }))).toThrowError(RowCapExceededError);
    });

    it('RowCapExceededError message includes the row count', () => {
      const bigRows = Array.from({ length: 5002 }, (_, i) => ({ name: `R${i}`, amount: i, qty: 1 }));
      let caught: unknown;
      try { service.exportCsv(makeExport({ rows: bigRows })); } catch (e) { caught = e; }
      expect(caught instanceof RowCapExceededError).toBeTrue();
      expect((caught as RowCapExceededError).message).toContain('5,002');
    });

    it('does not throw for exactly 5000 rows', () => {
      const rows = Array.from({ length: 5000 }, (_, i) => ({ name: `R${i}`, amount: i, qty: 1 }));
      expect(() => service.exportCsv(makeExport({ rows }))).not.toThrow();
    });
  });

  // -------------------------------------------------------------------------
  // CSV content — read the actual Blob text
  // -------------------------------------------------------------------------
  describe('exportCsv() — content', () => {
    /** Read the text of the Blob most recently passed to URL.createObjectURL. */
    async function lastCsvText(): Promise<string> {
      const blobs = capturedBlobs();
      expect(blobs.length).withContext('URL.createObjectURL must have been called with a Blob').toBeGreaterThan(0);
      return blobs.at(-1)!.text();
    }

    it('includes column header labels', async () => {
      service.exportCsv(makeExport({ totals: undefined }));
      expect(await lastCsvText()).toContain('Name,Amount,Qty');
    });

    it('starts with UTF-8 BOM bytes (0xEF 0xBB 0xBF)', async () => {
      service.exportCsv(makeExport({ totals: undefined }));
      // Blob.text() strips the BOM per spec — read raw bytes instead.
      const blobs = capturedBlobs();
      expect(blobs.length).toBeGreaterThan(0);
      const buf = await blobs.at(-1)!.arrayBuffer();
      const bytes = new Uint8Array(buf);
      expect(bytes[0]).toBe(0xEF);
      expect(bytes[1]).toBe(0xBB);
      expect(bytes[2]).toBe(0xBF);
    });

    it('wraps a cell containing a comma in double quotes', async () => {
      service.exportCsv(makeExport({ rows: [{ name: 'Smith, John', amount: 100, qty: 1 }], totals: undefined }));
      expect(await lastCsvText()).toContain('"Smith, John"');
    });

    it('escapes embedded double-quotes per RFC 4180', async () => {
      service.exportCsv(makeExport({ rows: [{ name: 'He said "hello"', amount: 0, qty: 1 }], totals: undefined }));
      expect(await lastCsvText()).toContain('"He said ""hello"""');
    });

    it('appends a totals row when totals are provided', async () => {
      service.exportCsv(makeExport());
      expect(await lastCsvText()).toContain('Total');
    });
  });

  // -------------------------------------------------------------------------
  // Excel
  // -------------------------------------------------------------------------
  describe('exportExcel()', () => {
    it('throws RowCapExceededError for > 5000 rows', async () => {
      const bigRows = Array.from({ length: 5001 }, (_, i) => ({ name: `R${i}`, amount: i, qty: 1 }));
      await expectAsync(service.exportExcel(makeExport({ rows: bigRows }))).toBeRejectedWithError(RowCapExceededError);
    });

    it('resolves without error for a normal payload (xlsx loaded)', async () => {
      // xlsx is installed — dynamic import succeeds in Karma bundle
      await expectAsync(service.exportExcel(makeExport())).toBeResolved();
    });
  });

  // -------------------------------------------------------------------------
  // PDF
  // -------------------------------------------------------------------------
  describe('exportPdf()', () => {
    it('throws RowCapExceededError for > 5000 rows', async () => {
      const bigRows = Array.from({ length: 5001 }, (_, i) => ({ name: `R${i}`, amount: i, qty: 1 }));
      await expectAsync(service.exportPdf(makeExport({ rows: bigRows }))).toBeRejectedWithError(RowCapExceededError);
    });

    it('resolves without error for a normal payload (jspdf loaded)', async () => {
      await expectAsync(service.exportPdf(makeExport())).toBeResolved();
    });

    it('resolves for a wide export with > 6 columns', async () => {
      const wideExport = makeExport({
        columns: Array.from({ length: 7 }, (_, i) => ({
          key: `col${i}`, label: `Col ${i}`, format: 'text' as const,
        })),
        rows: [Object.fromEntries(Array.from({ length: 7 }, (_, i) => [`col${i}`, `val${i}`]))],
        totals: undefined,
      });
      await expectAsync(service.exportPdf(wideExport)).toBeResolved();
    });
  });
});

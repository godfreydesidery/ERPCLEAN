import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReportExportMenuComponent } from './report-export-menu.component';
import { ReportExport, ReportExportService } from './report-export.service';
import { By } from '@angular/platform-browser';

function makeExport(): ReportExport {
  return {
    title: 'Test',
    columns: [{ key: 'name', label: 'Name', format: 'text' }],
    rows: [{ name: 'Row 1' }],
  };
}

describe('ReportExportMenuComponent', () => {
  let fixture: ComponentFixture<ReportExportMenuComponent>;
  let component: ReportExportMenuComponent;
  let exportService: jasmine.SpyObj<ReportExportService>;

  beforeEach(async () => {
    exportService = jasmine.createSpyObj<ReportExportService>('ReportExportService', [
      'exportCsv',
      'exportExcel',
      'exportPdf',
    ]);
    exportService.exportCsv.and.returnValue(undefined);
    exportService.exportExcel.and.returnValue(Promise.resolve());
    exportService.exportPdf.and.returnValue(Promise.resolve());

    await TestBed.configureTestingModule({
      imports: [ReportExportMenuComponent],
      providers: [{ provide: ReportExportService, useValue: exportService }],
    }).compileComponents();

    fixture = TestBed.createComponent(ReportExportMenuComponent);
    component = fixture.componentInstance;
    component.exportBuilder = makeExport;
    component.disabled = false;
    fixture.detectChanges();
  });

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------
  it('renders three export buttons', () => {
    const buttons = fixture.debugElement.queryAll(By.css('button'));
    expect(buttons.length).toBe(3);
  });

  it('has data-testid attributes for all three buttons', () => {
    const pdf   = fixture.nativeElement.querySelector('[data-testid="export-pdf"]');
    const excel = fixture.nativeElement.querySelector('[data-testid="export-excel"]');
    const csv   = fixture.nativeElement.querySelector('[data-testid="export-csv"]');
    expect(pdf).toBeTruthy();
    expect(excel).toBeTruthy();
    expect(csv).toBeTruthy();
  });

  it('renders inside a role=group button group', () => {
    const group = fixture.nativeElement.querySelector('[role="group"]');
    expect(group).toBeTruthy();
    expect(group.getAttribute('aria-label')).toBeTruthy();
  });

  // -------------------------------------------------------------------------
  // Disabled state
  // -------------------------------------------------------------------------
  it('marks buttons as aria-disabled when disabled=true', () => {
    component.disabled = true;
    fixture.detectChanges();

    const buttons: NodeListOf<HTMLButtonElement> = fixture.nativeElement.querySelectorAll('button');
    buttons.forEach(btn => {
      expect(btn.getAttribute('aria-disabled')).toBe('true');
    });
  });

  it('does not call service when disabled', fakeAsync(async () => {
    component.disabled = true;
    fixture.detectChanges();

    const csvBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="export-csv"]');
    csvBtn.click();
    tick();

    expect(exportService.exportCsv).not.toHaveBeenCalled();
  }));

  // -------------------------------------------------------------------------
  // CSV click
  // -------------------------------------------------------------------------
  it('calls exportCsv on CSV button click', fakeAsync(async () => {
    const csvBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="export-csv"]');
    csvBtn.click();
    tick();

    expect(exportService.exportCsv).toHaveBeenCalledOnceWith(makeExport());
  }));

  it('emits exportStarted then exportComplete for CSV', fakeAsync(async () => {
    const started: string[] = [];
    const completed: string[] = [];
    component.exportStarted.subscribe(f => started.push(f));
    component.exportComplete.subscribe(f => completed.push(f));

    const csvBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="export-csv"]');
    csvBtn.click();
    tick();

    expect(started).toEqual(['csv']);
    expect(completed).toEqual(['csv']);
  }));

  // -------------------------------------------------------------------------
  // Excel click
  // -------------------------------------------------------------------------
  it('calls exportExcel on Excel button click', fakeAsync(async () => {
    const excelBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="export-excel"]');
    excelBtn.click();
    tick();
    await fixture.whenStable();

    expect(exportService.exportExcel).toHaveBeenCalledOnceWith(makeExport());
  }));

  // -------------------------------------------------------------------------
  // PDF click
  // -------------------------------------------------------------------------
  it('calls exportPdf on PDF button click', fakeAsync(async () => {
    const pdfBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="export-pdf"]');
    pdfBtn.click();
    tick();
    await fixture.whenStable();

    expect(exportService.exportPdf).toHaveBeenCalledOnceWith(makeExport());
  }));

  // -------------------------------------------------------------------------
  // Error state
  // -------------------------------------------------------------------------
  it('emits exportFailed and shows error message when service throws', fakeAsync(async () => {
    exportService.exportCsv.and.throwError('boom');

    const failures: unknown[] = [];
    component.exportFailed.subscribe(e => failures.push(e));

    const csvBtn: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="export-csv"]');
    csvBtn.click();
    tick();
    fixture.detectChanges();

    expect(failures.length).toBe(1);
    const errorDiv = fixture.nativeElement.querySelector('[role="alert"]');
    expect(errorDiv).toBeTruthy();
  }));
});

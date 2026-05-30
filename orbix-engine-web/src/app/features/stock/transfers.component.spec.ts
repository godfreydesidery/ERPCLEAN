/**
 * Unit tests for TransfersComponent — receive leg (US-STOCK-007/008).
 * Covers:
 *   - receiveDraft population on select()
 *   - variance(), isVarianceLine(), totalVariance(), draftVarianceTotal()
 *   - hasReceiveDraft()
 *   - receive() builds correct request and calls StockService.receiveTransfer()
 *   - error state on receive() failure
 *   - issue() and close() happy paths
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { TransfersComponent } from './transfers.component';
import { StockService } from './stock.service';
import { AuthService } from '../../core/auth/auth.service';
import { StockTransfer } from './stock.models';
import { signal } from '@angular/core';

// ---------------------------------------------------------------------------
// Fixture data
// ---------------------------------------------------------------------------

function makeTransfer(overrides: Partial<StockTransfer> = {}): StockTransfer {
  return {
    id: '1', uid: '01JTEST00000000000ST1',
    number: 'ST-001', companyId: '1',
    fromBranchId: '10', toBranchId: '20',
    issuedAt: '2026-05-28T08:00:00Z', receivedAt: null,
    status: 'ISSUED',
    lines: [
      { id: 'L1', itemId: '101', issuedQty: 10, receivedQty: null, costAmount: 500 },
      { id: 'L2', itemId: '102', issuedQty: 5,  receivedQty: null, costAmount: 200 },
    ],
    ...overrides,
  };
}

const RECEIVED_TRANSFER: StockTransfer = makeTransfer({
  status: 'RECEIVED',
  receivedAt: '2026-05-28T10:00:00Z',
  lines: [
    { id: 'L1', itemId: '101', issuedQty: 10, receivedQty: 9,  costAmount: 500 },
    { id: 'L2', itemId: '102', issuedQty: 5,  receivedQty: 5,  costAmount: 200 },
  ],
});

// ---------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------

async function setup(opts: {
  transfers?: StockTransfer[];
  receiveResult?: StockTransfer;
  receiveError?: HttpErrorResponse;
} = {}) {
  const transfers = opts.transfers ?? [makeTransfer()];

  const stockSpy = jasmine.createSpyObj<StockService>('StockService', [
    'listTransfers', 'createTransfer', 'issueTransfer', 'receiveTransfer', 'closeTransfer',
  ]);
  stockSpy.listTransfers.and.returnValue(of(transfers));
  stockSpy.issueTransfer.and.returnValue(of(makeTransfer({ status: 'ISSUED' })));
  if (opts.receiveError) {
    stockSpy.receiveTransfer.and.returnValue(throwError(() => opts.receiveError));
  } else {
    stockSpy.receiveTransfer.and.returnValue(of(opts.receiveResult ?? RECEIVED_TRANSFER));
  }
  stockSpy.closeTransfer.and.returnValue(of(makeTransfer({ status: 'CLOSED', lines: RECEIVED_TRANSFER.lines })));

  const authStub = {
    currentUser: signal(null),
    hasPermission: (_: string) => false,
  } as unknown as AuthService;

  await TestBed.configureTestingModule({
    imports: [TransfersComponent, HttpClientTestingModule],
    providers: [
      provideRouter([]),
      { provide: StockService, useValue: stockSpy },
      { provide: AuthService, useValue: authStub },
    ],
  }).compileComponents();

  const fixture: ComponentFixture<TransfersComponent> = TestBed.createComponent(TransfersComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp: fixture.componentInstance, stockSpy };
}

async function stabilise(fixture: ComponentFixture<unknown>): Promise<void> {
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('TransfersComponent', () => {

  // -- select() + receiveDraft population ------------------------------------
  describe('select()', () => {
    it('populates receiveDraft with issuedQty when receivedQty is null', async () => {
      const { comp } = await setup();
      const transfer = makeTransfer();
      comp.select(transfer);

      const draft = (comp as unknown as { receiveDraft: Record<string, number | null> }).receiveDraft;
      expect(draft['L1']).toBe(10);
      expect(draft['L2']).toBe(5);
    });

    it('populates receiveDraft with receivedQty when already set', async () => {
      const { comp } = await setup();
      comp.select(RECEIVED_TRANSFER);

      const draft = (comp as unknown as { receiveDraft: Record<string, number | null> }).receiveDraft;
      expect(draft['L1']).toBe(9);
      expect(draft['L2']).toBe(5);
    });
  });

  // -- variance helpers ------------------------------------------------------
  describe('variance()', () => {
    it('returns 0 when receivedQty is null', async () => {
      const { comp } = await setup();
      const line = { id: 'L1', itemId: '101', issuedQty: 10, receivedQty: null, costAmount: 500 };
      expect(comp.variance(line)).toBe(0);
    });

    it('returns negative value for a shortage', async () => {
      const { comp } = await setup();
      const line = { id: 'L1', itemId: '101', issuedQty: 10, receivedQty: 8, costAmount: 500 };
      expect(comp.variance(line)).toBe(-2);
    });

    it('returns positive value for a surplus', async () => {
      const { comp } = await setup();
      const line = { id: 'L1', itemId: '101', issuedQty: 5, receivedQty: 6, costAmount: 200 };
      expect(comp.variance(line)).toBe(1);
    });

    it('returns 0 when received equals issued', async () => {
      const { comp } = await setup();
      const line = { id: 'L1', itemId: '101', issuedQty: 10, receivedQty: 10, costAmount: 500 };
      expect(comp.variance(line)).toBe(0);
    });
  });

  describe('isVarianceLine()', () => {
    it('returns false when receivedQty is null', async () => {
      const { comp } = await setup();
      const line = { id: 'L1', itemId: '101', issuedQty: 10, receivedQty: null, costAmount: 500 };
      expect(comp.isVarianceLine(line)).toBe(false);
    });

    it('returns false when received equals issued', async () => {
      const { comp } = await setup();
      const line = { id: 'L1', itemId: '101', issuedQty: 10, receivedQty: 10, costAmount: 500 };
      expect(comp.isVarianceLine(line)).toBe(false);
    });

    it('returns true when received differs from issued', async () => {
      const { comp } = await setup();
      const line = { id: 'L1', itemId: '101', issuedQty: 10, receivedQty: 9, costAmount: 500 };
      expect(comp.isVarianceLine(line)).toBe(true);
    });
  });

  describe('totalVariance()', () => {
    it('returns net variance summed across all lines', async () => {
      const { comp } = await setup();
      // L1: 9-10 = -1, L2: 5-5 = 0 => net = -1
      expect(comp.totalVariance(RECEIVED_TRANSFER)).toBe(-1);
    });

    it('returns 0 when all received quantities match issued', async () => {
      const { comp } = await setup();
      const t = makeTransfer({
        status: 'RECEIVED',
        lines: [
          { id: 'L1', itemId: '101', issuedQty: 10, receivedQty: 10, costAmount: 500 },
          { id: 'L2', itemId: '102', issuedQty: 5,  receivedQty: 5,  costAmount: 200 },
        ],
      });
      expect(comp.totalVariance(t)).toBe(0);
    });
  });

  describe('draftVarianceTotal()', () => {
    it('returns 0 when draft matches issued', async () => {
      const { comp } = await setup();
      const transfer = makeTransfer();
      comp.select(transfer); // seeds draft with issuedQty
      expect(comp.draftVarianceTotal(transfer)).toBe(0);
    });

    it('reflects edits made to receiveDraft', async () => {
      const { comp } = await setup();
      const transfer = makeTransfer();
      comp.select(transfer);
      const draft = (comp as unknown as { receiveDraft: Record<string, number | null> }).receiveDraft;
      draft['L1'] = 8; // shortage of 2
      // net = (8-10) + (5-5) = -2
      expect(comp.draftVarianceTotal(transfer)).toBe(-2);
    });
  });

  // -- hasReceiveDraft() -----------------------------------------------------
  describe('hasReceiveDraft()', () => {
    it('returns false when no draft exists', async () => {
      const { comp } = await setup();
      expect(comp.hasReceiveDraft()).toBe(false);
    });

    it('returns true after select() seeds the draft', async () => {
      const { comp } = await setup();
      comp.select(makeTransfer());
      expect(comp.hasReceiveDraft()).toBe(true);
    });
  });

  // -- receive() — happy path -----------------------------------------------
  describe('receive() — happy path', () => {
    it('calls StockService.receiveTransfer() with the correct uid and line payload', async () => {
      const { comp, stockSpy } = await setup();
      comp.select(makeTransfer());

      // Override draft so L1 has a shortage
      const draft = (comp as unknown as { receiveDraft: Record<string, number | null> }).receiveDraft;
      draft['L1'] = 8;
      draft['L2'] = 5;

      comp.receive(makeTransfer());

      expect(stockSpy.receiveTransfer).toHaveBeenCalledOnceWith(
        '01JTEST00000000000ST1',
        {
          lines: [
            { lineId: 'L1', receivedQty: 8 },
            { lineId: 'L2', receivedQty: 5 },
          ],
        },
      );
    });

    it('updates selected() with the returned transfer after success', async () => {
      const { fixture, comp } = await setup();
      comp.select(makeTransfer());
      comp.receive(makeTransfer());
      await stabilise(fixture);

      const sel = (comp as unknown as { selected: () => StockTransfer | null }).selected();
      expect(sel?.status).toBe('RECEIVED');
    });

    it('clears busy() after success', async () => {
      const { fixture, comp } = await setup();
      comp.select(makeTransfer());
      comp.receive(makeTransfer());
      await stabilise(fixture);

      const c = comp as unknown as { busy: () => boolean };
      expect(c.busy()).toBe(false);
    });
  });

  // -- receive() — error path -----------------------------------------------
  describe('receive() — error path', () => {
    it('sets error() and clears busy() on HTTP failure', async () => {
      const err = new HttpErrorResponse({
        status: 400,
        error: { message: 'Transfer already received.', status: false, statusCode: 400, responseCode: 'ERR', errors: [] },
      });
      const { fixture, comp } = await setup({ receiveError: err });
      comp.select(makeTransfer());
      comp.receive(makeTransfer());
      await stabilise(fixture);

      const c = comp as unknown as { busy: () => boolean; error: () => string | null };
      expect(c.busy()).toBe(false);
      expect(c.error()).toBe('Transfer already received.');
    });
  });

  // -- issue() happy path ----------------------------------------------------
  describe('issue()', () => {
    it('calls StockService.issueTransfer() with the transfer uid', async () => {
      const { comp, stockSpy } = await setup();
      comp.issue(makeTransfer());
      expect(stockSpy.issueTransfer).toHaveBeenCalledOnceWith('01JTEST00000000000ST1');
    });
  });

  // -- close() happy path ----------------------------------------------------
  describe('close()', () => {
    it('calls StockService.closeTransfer() with the transfer uid', async () => {
      const { comp, stockSpy } = await setup({ transfers: [RECEIVED_TRANSFER] });
      comp.close(RECEIVED_TRANSFER);
      expect(stockSpy.closeTransfer).toHaveBeenCalledOnceWith('01JTEST00000000000ST1');
    });
  });

  // -- rendering: receive inputs shown when ISSUED ---------------------------
  describe('receive inputs', () => {
    it('renders an editable input per line when status is ISSUED', async () => {
      const { fixture, comp } = await setup();
      comp.select(makeTransfer());
      await stabilise(fixture);

      const inputs: NodeListOf<HTMLInputElement> =
        fixture.nativeElement.querySelectorAll('input[aria-label^="Received quantity"]');
      expect(inputs.length).toBe(2);
    });

    it('renders read-only text (not inputs) when status is RECEIVED', async () => {
      const { fixture, comp } = await setup();
      comp.select(RECEIVED_TRANSFER);
      await stabilise(fixture);

      const inputs: NodeListOf<HTMLInputElement> =
        fixture.nativeElement.querySelectorAll('input[aria-label^="Received quantity"]');
      expect(inputs.length).toBe(0);
    });
  });
});

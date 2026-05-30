/**
 * Unit tests for ISSUE-PARTY-002 fix — price-list picker in the customer form.
 *
 * Verifies:
 * 1. Price lists are loaded from CatalogService on init.
 * 2. The picker options reflect active price lists only.
 * 3. selectedPriceListId is sent in the create payload.
 * 4. selectedPriceListId is pre-populated from the existing customer on edit.
 * 5. selectedPriceListId is cleared on form reset.
 */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CustomersComponent } from './customers.component';
import { PartyService } from './party.service';
import { CatalogService } from '../catalog/catalog.service';
import { PriceList } from '../catalog/catalog.models';
import { Customer, PartyResponse } from './party.models';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const RETAIL_PL: PriceList = {
  id: '1', uid: '01JPL00000000000001', companyId: '1',
  code: 'RETAIL', name: 'Retail Price List',
  currencyCode: 'TZS', validFrom: '2024-01-01', validTo: null,
  isDefault: true, taxInclusive: true, status: 'ACTIVE',
};
const ARCHIVED_PL: PriceList = {
  id: '2', uid: '01JPL00000000000002', companyId: '1',
  code: 'OLD', name: 'Legacy List',
  currencyCode: 'TZS', validFrom: '2023-01-01', validTo: '2023-12-31',
  isDefault: false, taxInclusive: false, status: 'ARCHIVED',
};

function makeCustomer(priceListId: string | null): Customer {
  const party: PartyResponse = {
    id: '42', uid: '01JPAR00000000000042', companyId: '1',
    code: 'CUST0001', name: 'Test Customer',
    legalName: null, category: 'BUSINESS', tin: null, vrn: null,
    phone: null, email: null, physicalAddress: null, postalAddress: null,
    countryCode: null, notes: null, status: 'ACTIVE',
  };
  return {
    partyId: '42', party,
    creditLimitAmount: 100000, creditTermsDays: 30,
    priceListId, defaultSalesAgentId: null, defaultBranchId: null,
    walkIn: false, taxExempt: false,
  };
}

// ---------------------------------------------------------------------------
// Setup helper
// ---------------------------------------------------------------------------

interface SpyBag {
  partySpy: jasmine.SpyObj<PartyService>;
  catalogSpy: jasmine.SpyObj<CatalogService>;
}

async function setup(): Promise<{ fixture: ComponentFixture<CustomersComponent>; comp: CustomersComponent; spies: SpyBag }> {
  const partySpy = jasmine.createSpyObj<PartyService>('PartyService', [
    'listCustomers', 'listParties', 'createCustomer', 'updateCustomer',
    'archiveCustomer', 'activateCustomer', 'findByTin',
  ]);
  partySpy.listCustomers.and.returnValue(of({
    content: [], page: 0, size: 20, totalElements: 0, totalPages: 0,
  }));
  partySpy.listParties.and.returnValue(of([]));
  partySpy.createCustomer.and.returnValue(of(makeCustomer(null)));
  partySpy.updateCustomer.and.returnValue(of(makeCustomer(RETAIL_PL.id)));

  const catalogSpy = jasmine.createSpyObj<CatalogService>('CatalogService', ['listPriceLists']);
  catalogSpy.listPriceLists.and.returnValue(of([RETAIL_PL, ARCHIVED_PL]));

  await TestBed.configureTestingModule({
    imports: [CustomersComponent, HttpClientTestingModule],
    providers: [
      provideRouter([]),
      { provide: PartyService, useValue: partySpy },
      { provide: CatalogService, useValue: catalogSpy },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(CustomersComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();

  return { fixture, comp: fixture.componentInstance, spies: { partySpy, catalogSpy } };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('CustomersComponent — price-list picker (ISSUE-PARTY-002)', () => {

  describe('initialisation', () => {
    it('calls CatalogService.listPriceLists on init', async () => {
      const { spies } = await setup();
      expect(spies.catalogSpy.listPriceLists).toHaveBeenCalledTimes(1);
    });

    it('exposes only ACTIVE price lists as picker options', async () => {
      const { comp } = await setup();
      const opts = comp['priceListOptions']();
      expect(opts.length).toBe(1);
      expect(opts[0].id).toBe(RETAIL_PL.id);
      expect(opts[0].label).toContain(RETAIL_PL.code);
    });
  });

  describe('create form', () => {
    it('starts with selectedPriceListId = null', async () => {
      const { comp } = await setup();
      expect(comp['selectedPriceListId']).toBeNull();
    });

    it('passes selectedPriceListId to createCustomer when set', fakeAsync(async () => {
      const { comp, spies } = await setup();

      // Simulate the form being open in create-new mode.
      comp['showForm'].set(true);
      comp['partyMode'].set('create');
      comp['selectedPriceListId'] = RETAIL_PL.id;

      // Trigger submit via the private runCreate path.
      (comp as unknown as { runCreate(): void }).runCreate();
      tick();

      expect(spies.partySpy.createCustomer).toHaveBeenCalledWith(
        jasmine.objectContaining({ priceListId: RETAIL_PL.id })
      );
    }));

    it('passes null priceListId when no price list is selected', fakeAsync(async () => {
      const { comp, spies } = await setup();

      comp['showForm'].set(true);
      comp['partyMode'].set('create');
      comp['selectedPriceListId'] = null;

      (comp as unknown as { runCreate(): void }).runCreate();
      tick();

      expect(spies.partySpy.createCustomer).toHaveBeenCalledWith(
        jasmine.objectContaining({ priceListId: null })
      );
    }));
  });

  describe('edit form', () => {
    it('pre-populates selectedPriceListId from existing customer on startEdit', async () => {
      const { comp } = await setup();
      const customer = makeCustomer(RETAIL_PL.id);
      comp.startEdit(customer);

      expect(comp['selectedPriceListId']).toBe(RETAIL_PL.id);
    });

    it('pre-populates null when customer has no price list', async () => {
      const { comp } = await setup();
      const customer = makeCustomer(null);
      comp.startEdit(customer);

      expect(comp['selectedPriceListId']).toBeNull();
    });

    it('passes selectedPriceListId to updateCustomer', fakeAsync(async () => {
      const { comp, spies } = await setup();
      const customer = makeCustomer(null);
      comp.startEdit(customer);
      comp['selectedPriceListId'] = RETAIL_PL.id;

      (comp as unknown as { runUpdate(uid: string): void }).runUpdate(customer.party.uid);
      tick();

      expect(spies.partySpy.updateCustomer).toHaveBeenCalledWith(
        customer.party.uid,
        jasmine.objectContaining({ priceListId: RETAIL_PL.id })
      );
    }));
  });

  describe('reset', () => {
    it('clears selectedPriceListId on reset', async () => {
      const { comp } = await setup();
      comp['selectedPriceListId'] = RETAIL_PL.id;
      (comp as unknown as { reset(): void }).reset();
      expect(comp['selectedPriceListId']).toBeNull();
    });
  });
});

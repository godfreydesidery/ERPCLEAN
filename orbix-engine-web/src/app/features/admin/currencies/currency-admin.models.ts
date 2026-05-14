/** Mirrors the backend admin currency/FX DTOs (see CurrencyController, FxRateController). */

export interface Currency {
  code: string;
  name: string;
  symbol: string | null;
  minorUnitDigits: number;
  status: string;
}

export interface CreateCurrencyRequest {
  code: string;
  name: string;
  symbol: string | null;
  minorUnitDigits: number;
}

export interface FxRate {
  id: number;
  fromCurrency: string;
  toCurrency: string;
  rate: number;
  effectiveAt: string;
}

export interface QuoteFxRateRequest {
  fromCurrency: string;
  toCurrency: string;
  rate: number;
  effectiveAt: string;
}

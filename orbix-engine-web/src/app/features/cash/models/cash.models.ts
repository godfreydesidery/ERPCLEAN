/**
 * Cash module wire models. Mirrors the backend DTOs:
 *   - {@code CashEntryDto} (append-only ledger row)
 *   - {@code CashBookDto} (per-branch/account/currency/day balance projection)
 *   - {@code CashAdjustmentDto} + {@code BankDepositDto} (audit-docs w/ reversal)
 *
 * Identity discipline (CLAUDE.md):
 *   - {@code uid} is the URL handle.
 *   - Every Long id field is typed as {@code string} (Jackson stringifies on the wire).
 *   - {@code BigDecimal} amounts arrive as strings; keep them as strings so we
 *     never round-trip through JS Number on display.
 */

export type CashAccount = 'TILL' | 'CASH_BOX' | 'BANK' | 'MOBILE_MONEY';
export type CashDirection = 'IN' | 'OUT';
export type GlCategory =
  | 'CASH'
  | 'BANK'
  | 'PETTY'
  | 'VARIANCE'
  | 'SUPPLIER_SETTLEMENT'
  | 'RECEIPT'
  | 'TILL_FLOAT'
  | 'CASH_REFUND'
  | 'GIFT_CARD_ISSUE_PROCEEDS'
  | 'FX_VARIANCE'
  | 'ORDER_DEPOSIT'
  | 'ADJUSTMENT';

export const CASH_ACCOUNTS: readonly CashAccount[] = ['TILL', 'CASH_BOX', 'BANK', 'MOBILE_MONEY'];

/**
 * Composite-PK aggregate (branchId, account, currencyCode, businessDate) per
 * ADR 0002. uid is the external URL handle; no surrogate id field.
 */
export interface CashBook {
  uid: string;
  branchId: string;
  account: CashAccount;
  businessDate: string;
  currencyCode: string;
  openingAmount: string;
  inAmount: string;
  outAmount: string;
  closingAmount: string;
}

/** Append-only ledger row. Immutable wire shape — no archive lifecycle. */
export interface CashEntry {
  uid: string;
  id: string;
  at: string;
  companyId: string;
  branchId: string;
  businessDate: string;
  account: CashAccount;
  direction: CashDirection;
  amount: string;
  tenderAmount: string;
  fxRateSnapshot: string;
  currencyCode: string;
  refType: string;
  refId: string | null;
  glCategory: GlCategory;
  notes: string | null;
  actorId: string | null;
}

export interface CashAdjustment {
  uid: string;
  id: string;
  companyId: string;
  branchId: string;
  businessDate: string;
  account: CashAccount;
  direction: CashDirection;
  amount: string;
  currencyCode: string;
  reason: string;
  at: string;
  postedBy: string;
  reversedAt: string | null;
  reversedBy: string | null;
  reversedByEntryId: string | null;
}

export interface BankDeposit {
  uid: string;
  id: string;
  companyId: string;
  branchId: string;
  businessDate: string;
  amount: string;
  currencyCode: string;
  reference: string;
  notes: string | null;
  at: string;
  postedBy: string;
  reversedAt: string | null;
  reversedBy: string | null;
  reversedByOutEntryId: string | null;
  reversedByInEntryId: string | null;
}

export interface PostCashAdjustmentRequest {
  branchId: string;
  account: CashAccount;
  direction: CashDirection;
  amount: string | number;
  reason: string;
}

export interface PostBankDepositRequest {
  branchId: string;
  amount: string | number;
  reference: string;
  notes: string | null;
}

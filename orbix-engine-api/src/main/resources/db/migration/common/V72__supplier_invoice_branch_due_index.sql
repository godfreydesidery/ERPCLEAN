-- Slice G.1 — AP-aging backing index. Mirrors ix_sales_invoice_branch_due (V27).
-- Supports findAllOpenForAging + findOpenForSupplier used by SupplierDebtReadModelServiceImpl.
CREATE INDEX ix_supplier_invoice_branch_due
    ON supplier_invoice (company_id, branch_id, due_date, status);

# Domain Event Schemas

JSON Schemas for every domain event the system emits. See [ARCHITECTURE.md §2.10](../../ARCHITECTURE.md).

## Conventions

- Filename: `<EventType>.<vN>.json`. Examples: `SalesInvoicePosted.v1.json`, `BatchProductionCompleted.v1.json`.
- A schema change requires a **new version file**, never an edit of an existing version. Subscribers consume versioned types explicitly.
- Each schema declares: required IDs, business-relevant fields, the producing module, and an example payload.

## Listing

- `SalesInvoicePosted.v1.json`
- `GrnPosted.v1.json`
- `ReceiptAllocated.v1.json`
- `DebtAllocated.v1.json`
- `BatchProductionCompleted.v1.json`
- `TillSessionClosed.v1.json`
- `SalesSheetApproved.v1.json`
- `ItemCreated.v1.json`
- `PriceChanged.v1.json`
- `FeatureFlagToggled.v1.json`

(Files land here as each module ships.)

# orbix-engine-contracts

Source of truth for the Orbix Engine REST API and domain events.

## Layout

```
orbix-engine-contracts/
├── openapi/
│   └── orbix-engine.yaml      OpenAPI 3.1 — every public endpoint
├── events/
│   └── *.json                 JSON Schema per domain-event type, versioned
├── generated/
│   ├── ts/                    TypeScript client (consumed by orbix-engine-web)
│   └── dart/                  Dart client (consumed by pos & wms)
└── scripts/
    └── generate.sh            Regenerates clients from the spec
```

## Workflow

1. API changes start as edits to `openapi/orbix-engine.yaml`.
2. PR triggers regeneration of TS + Dart clients.
3. The backend implements the new spec; the contract test (ARCHITECTURE.md §9) asserts that the generated server stubs match implemented endpoints.
4. A breaking change is **never** silent: the spec linter blocks merges that delete or change a field without a version bump.

# Architecture Decision Records

Every non-trivial architecture decision is recorded here.

## Why ADRs

The legacy ERP-master codebase had no record of *why* anything was the way it was — every decision required archaeology. ADRs fix this: when a future engineer wonders "why MySQL and not Postgres?", the answer is one file away.

## Numbering

`NNNN-short-slug.md`. Numbers are 4-digit, monotonically incrementing. Once an ADR has an ID, it never moves.

## Status lifecycle

`Proposed` → `Accepted` → (`Superseded by NNNN` | `Deprecated`).

ADRs are **never deleted**. If a decision is reversed, the old ADR keeps its place and the new ADR cites it.

## How to write one

1. Copy [`0000-adr-template.md`](0000-adr-template.md) to `NNNN-your-slug.md`.
2. Fill in Context, Decision, Consequences, Alternatives.
3. PR it. Reviewers focus on whether the reasoning holds, not whether they would have chosen the same option.
4. On merge, status becomes `Accepted`.

## Index

| # | Title | Status |
|---|---|---|
| 0001 | [Modular monolith over microservices](0001-modular-monolith.md) | Accepted |

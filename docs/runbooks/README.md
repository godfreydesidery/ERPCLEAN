# Runbooks

Operational procedures, written so an on-call engineer can execute them at 3 a.m. without context.

## Listing (to be authored as features ship)

- `backup-restore.md` — restore the DB from a backup into staging and verify (monthly drill)
- `cut-over.md` — migrate one branch from legacy to the new system
- `secret-rotation.md` — rotate JWT signing key, DB password, Meili master key
- `incident-response.md` — paging policy, comms templates, retrospective procedure
- `database-failover.md` — promote a read replica when the primary fails
- `pos-rollback.md` — revert a bad POS release on a fleet of tills

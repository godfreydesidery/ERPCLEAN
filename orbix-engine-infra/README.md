# orbix-engine-infra

Infrastructure-as-code for local development and deployments.

## Layout

```
orbix-engine-infra/
├── docker/        compose for local + helper images
└── deploy/        IaC for staging / production (Terraform / Pulumi — per deployment)
```

## Local development

Use the root [`docker-compose.yml`](../docker-compose.yml) for local infrastructure (MySQL, Postgres, Redis, Meilisearch, MinIO). This folder hosts the production deployment templates.

## Deployment

Each customer gets a single-tenant deployment. Recommended targets:
- Self-hosted Linux VM (compose-based)
- Customer's cloud account (Terraform modules per provider)
- Our managed cloud (in-house)

The deployment artefact is one OCI image per app:
- `orbix-engine-api:<version>`
- `orbix-engine-web:<version>` (nginx + static bundle)

POS and WMS distribute through their own update channels (see ARCHITECTURE.md §7.6).

# orbix-engine-web

Back-office Web ERP. Angular 17 standalone-components, Bootstrap 5, lazy-loaded feature modules.

## Run

```bash
npm install
npm start
```
Available at http://localhost:4200. Backend must be reachable at http://localhost:8081/api/v1.

## Build

```bash
npm run build       # production build
```

## Test

```bash
npm test            # unit + axe-core accessibility checks
npm run e2e         # Playwright critical-flow tests
```

## Layout

```
src/app
├── app.config.ts        Application bootstrap providers
├── app.routes.ts        Top-level routes; each feature lazy-loads
├── app.component.ts     Root host
├── layout/              Shell + nav
├── core/
│   ├── auth/            AuthService, interceptor, guard
│   └── error/           Global error interceptor
└── features/
    ├── auth/login
    ├── dashboard
    ├── catalog          PRD §5.3
    ├── sales            PRD §5.6
    ├── procurement      PRD §5.5
    ├── stock            PRD §5.4
    ├── production       PRD §5.10
    ├── debt             PRD §5.9
    ├── reports          PRD §5.13
    └── admin            users, roles, flags, settings
```

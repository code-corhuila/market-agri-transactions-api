# market-agri-transactions-api

Transactions service API for the **Marketplace Agrícola Huila** distributed system. The service
scaffold uses Java 21, Spring Boot, and three Maven modules to keep domain, application, and adapter
code separate.

## Modules

```
domain/          transactions domain types and rules; no framework dependencies
application/     use cases and inbound/outbound ports; depends on domain
infrastructure/  Spring Boot entry point, HTTP adapters, and runtime configuration
```

The API does not own database schema or migrations. Those live in `market-agri-transactions-db`;
database changes are applied deliberately before deploying this service.

## Run locally

```bash
mvn -B -ntp verify
docker compose -f deploy/compose.yml config
docker compose -f deploy/compose.yml up --build
```

The container exposes port 8080 only inside the platform network. `GET /health` returns the service
health. Requests receive an `X-Correlation-Id`; a valid supplied value is preserved, otherwise the
service generates a UUID. API errors use a consistent JSON envelope.

## Branching

Work enters `develop` through a child branch and Pull Request. Promotion to `qa` and `main` follows
the project Git guide and uses re-application with `cherry-pick -x`; do not merge permanent branches.

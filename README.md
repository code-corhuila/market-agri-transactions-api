# market-agri-transactions-api

Service API of the **transactions** domain (payments, ledger, Stripe webhook, outbox) of the
**Marketplace Agrícola Huila** distributed system: Java 21, Spring Boot 3.5, hexagonal architecture in
three Maven modules (Anexo C). Contract: `market-agri-docs/07-api/api-contract.md` §4.4.

## Modules

```
transactions-core/      domain/model, application/port/{in,out}, application/usecase — plain Java, no framework
transactions-adapters/  adapter/in/http, adapter/out/persistence
transactions-app/       composition root: entry point, wiring and every limit (application.yml)
deploy/                 Dockerfile and compose.yml, included by market-agri-infra (no host port)
```

`transactions-core` declares no framework: a Spring or JDBC type there does not compile. The schema and
its migrations live in `market-agri-transactions-db` (ADR-011); this service connects as
`transactions_app` and never migrates.

## Run locally

```bash
./mvnw -B verify                      # build and tests (Windows: mvnw.cmd)
java -jar transactions-app/target/transactions-app-0.0.1-SNAPSHOT.jar
curl -i http://localhost:8080/health  # liveness, no token
```

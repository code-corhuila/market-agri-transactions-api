# market-agri-transactions-api

Service API of the **transactions** domain (payments, ledger, Wompi webhook, outbox; ADR-019) of the
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

## What the scaffold already does

| Rule | Where |
|---|---|
| `GET /health` → `200 {"status":"UP"}` without token (liveness, E-60) | `adapter/in/http/HealthController` |
| One error body `{error, message, details, traceId}` from **one** handler (D-C21) | `adapter/in/http/ErrorHandler` |
| `X-Correlation-Id` reused or generated, returned, as `traceId` and in every JSON log line (D-C28) | `adapter/in/http/CorrelationFilter` |
| `GET /health/ready` → `200`/`503` with `checks.database` and `checks.broker` (E-61) | `adapter/in/http/HealthController`, `JdbcDatabaseCheck` |
| I-06 `POST /internal/v1/transactions` (`SERVICE` only): `201`/`200` with `{transaction, checkoutUrl}` | `adapter/in/http/InternalTransactionController` |
| E-41 `GET /api/v1/transactions/{id}` (participant only), E-42 `GET /api/v1/transactions/mine` (purchases or sales), E-44 and E-45 (ADMIN reads) | `adapter/in/http/TransactionController`, `JdbcTransactionRepository` |
| E-43 `POST /api/v1/transactions/webhooks/payment-gateway` (anonymous): Wompi's checksum, `CONFIRMED`/`FAILED` and the event in the outbox in one transaction; `200` for every signed event (ADR-019, ADR-014) | `adapter/in/http/PaymentWebhookController`, `WompiEventReader`, `JdbcPaymentSettlements` |
| Outbox relay: every second, oldest first, to `market-agri.events` with publisher confirms as `transactions_app`; `published_at` only after the confirm; nothing declared (ADR-014) | `OutboxRelay`, `PublishPendingEventsService`, `adapter/out/messaging/RabbitEventPublisher` |
| Expiry job: every minute, a `PENDING` payment 5 minutes past its checkout is checked with Wompi by reference (private key); a final result is applied as E-43 would, otherwise `FAILED` + `TransactionFailed` (`EXPIRED`); no answer keeps it `PENDING` (FR-025) | `ExpiryJob`, `SettleExpiredPaymentsService`, `adapter/out/payment/WompiPaymentLookup` |
| Checkout link of Wompi Web Checkout signed with SHA-256, or the fake one, by `PAYMENT_GATEWAY` (ADR-019) | `adapter/out/payment/WompiPaymentGateway`, `FakePaymentGateway` |
| Payment and `Idempotency-Key` in one transaction; the partial index → `TRANSACTION_IN_PROGRESS` (I-06, D-21) | `adapter/out/persistence/JdbcTransactionCreations` |

## Run locally

```bash
./mvnw -B verify                      # build, unit and HTTP tests (Windows: mvnw.cmd)
cp .env.example .env                  # DATABASE_* of a database where transactions-db is migrated
set -a && . ./.env && set +a && java -jar transactions-app/target/transactions-app-0.0.1-SNAPSHOT.jar
curl -i http://localhost:8080/health/ready
```

## Integration tests

They run against a real PostgreSQL with the schema of `market-agri-transactions-db`, reached through
`TEST_DATABASE_URL` (Anexo C), and are skipped when it is not set. With Docker and transactions-db
cloned next to this repository:

```bash
export "$(bash scripts/test-database.sh)"   # throwaway container on 127.0.0.1:55433, migrated
./mvnw -B verify -Pintegration              # unit, HTTP and *IT
bash scripts/test-database.sh down
```

In CI, the `integration` job does the same on every pull request to `qa`.

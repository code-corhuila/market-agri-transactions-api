package co.edu.corhuila.marketagri.transactions.adapter.out.persistence;

import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentSettlements;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionEvent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link PaymentSettlements} on {@code payment_transaction} and {@code outbox_event} (B2,
 * ADR-014), as transactions_app. The row is locked with {@code FOR UPDATE} until the unit of work
 * ends, so two results of the same payment are applied one after the other.
 */
public final class JdbcPaymentSettlements implements PaymentSettlements {

    private final JdbcClient jdbc;
    private final OutboxEnvelopes envelopes;

    public JdbcPaymentSettlements(JdbcClient jdbc, OutboxEnvelopes envelopes) {
        this.jdbc = jdbc;
        this.envelopes = envelopes;
    }

    @Override
    public Optional<Transaction> lockById(UUID id) {
        return jdbc.sql("SELECT * FROM transactions.payment_transaction WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(JdbcTransactionCreations::transaction)
                .optional();
    }

    @Override
    public boolean gatewayTransactionIdTakenByAnother(String gatewayTransactionId, UUID transactionId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM transactions.payment_transaction"
                        + " WHERE gateway_transaction_id = :gatewayId AND id <> :id)")
                .param("gatewayId", gatewayTransactionId)
                .param("id", transactionId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public void settle(Transaction settled, TransactionEvent event) {
        jdbc.sql("""
                        UPDATE transactions.payment_transaction
                           SET status = :status, payment_method = :method, gateway_transaction_id = :gatewayId,
                               confirmed_at = :confirmedAt, failed_at = :failedAt, updated_at = now()
                         WHERE id = :id""")
                .param("status", settled.status().name())
                .param("method", settled.paymentMethod() == null ? null : settled.paymentMethod().name())
                .param("gatewayId", settled.gatewayTransactionId())
                .param("confirmedAt", utc(settled.confirmedAt()))
                .param("failedAt", utc(settled.failedAt()))
                .param("id", settled.id())
                .update();
        jdbc.sql("""
                        INSERT INTO transactions.outbox_event (event_id, event_type, routing_key, envelope, occurred_at)
                        VALUES (:eventId, :eventType, :routingKey, CAST(:envelope AS jsonb), :occurredAt)""")
                .param("eventId", event.eventId())
                .param("eventType", event.type().eventType())
                .param("routingKey", OutboxEnvelopes.routingKey(event.type()))
                .param("envelope", envelopes.json(event))
                .param("occurredAt", utc(event.occurredAt()))
                .update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}

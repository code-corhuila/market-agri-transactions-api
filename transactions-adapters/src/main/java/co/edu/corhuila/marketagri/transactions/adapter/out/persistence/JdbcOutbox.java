package co.edu.corhuila.marketagri.transactions.adapter.out.persistence;

import co.edu.corhuila.marketagri.transactions.application.port.out.Outbox;
import co.edu.corhuila.marketagri.transactions.domain.model.OutboxMessage;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** {@link Outbox} on {@code outbox_event} (ADR-014); the partial index {@code idx_outbox_event_unpublished} serves the reads. */
public final class JdbcOutbox implements Outbox {

    private final JdbcClient jdbc;

    public JdbcOutbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OutboxMessage> pending(int limit) {
        return jdbc.sql("""
                        SELECT event_id, routing_key, envelope::text AS envelope FROM transactions.outbox_event
                         WHERE published_at IS NULL ORDER BY occurred_at, event_id LIMIT :limit""")
                .param("limit", limit)
                .query((row, number) -> new OutboxMessage(row.getObject("event_id", UUID.class),
                        row.getString("routing_key"), row.getString("envelope")))
                .list();
    }

    @Override
    public void markPublished(UUID eventId, Instant at) {
        jdbc.sql("UPDATE transactions.outbox_event SET published_at = :at WHERE event_id = :id")
                .param("at", at.atOffset(ZoneOffset.UTC))
                .param("id", eventId)
                .update();
    }

    @Override
    public void recordFailedAttempt(UUID eventId) {
        jdbc.sql("UPDATE transactions.outbox_event SET attempts = attempts + 1 WHERE event_id = :id")
                .param("id", eventId)
                .update();
    }
}

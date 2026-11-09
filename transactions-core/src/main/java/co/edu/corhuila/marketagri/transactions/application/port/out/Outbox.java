package co.edu.corhuila.marketagri.transactions.application.port.out;

import co.edu.corhuila.marketagri.transactions.domain.model.OutboxMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The events waiting in the outbox (ADR-014), read and updated by the relay. */
public interface Outbox {

    /** At most {@code limit} events not published yet, oldest first. */
    List<OutboxMessage> pending(int limit);

    void markPublished(UUID eventId, Instant at);

    void recordFailedAttempt(UUID eventId);
}

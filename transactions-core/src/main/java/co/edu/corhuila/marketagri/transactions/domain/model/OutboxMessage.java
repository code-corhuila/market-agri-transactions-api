package co.edu.corhuila.marketagri.transactions.domain.model;

import java.util.UUID;

/** A row of the outbox as the relay publishes it: the envelope was built when the event was written (ADR-014). */
public record OutboxMessage(UUID eventId, String routingKey, String envelope) {
}

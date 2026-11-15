package co.edu.corhuila.marketagri.transactions.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.marketagri.transactions.application.port.out.EventPublisher;
import co.edu.corhuila.marketagri.transactions.application.port.out.Outbox;
import co.edu.corhuila.marketagri.transactions.domain.model.EventPublishException;
import co.edu.corhuila.marketagri.transactions.domain.model.OutboxMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The outbox relay (ADR-014): oldest first, an event is marked published only after the broker
 * confirmed it, and the first failure stops the run so the order is kept for the next one.
 */
class PublishPendingEventsServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T20:00:00Z");

    private static final class FakeOutbox implements Outbox {
        final Map<UUID, OutboxMessage> pending = new LinkedHashMap<>();
        final Map<UUID, Instant> published = new HashMap<>();
        final Map<UUID, Integer> attempts = new HashMap<>();
        int askedLimit;

        @Override
        public List<OutboxMessage> pending(int limit) {
            askedLimit = limit;
            return pending.values().stream().filter(message -> !published.containsKey(message.eventId()))
                    .limit(limit).toList();
        }

        @Override
        public void markPublished(UUID eventId, Instant at) {
            published.put(eventId, at);
        }

        @Override
        public void recordFailedAttempt(UUID eventId) {
            attempts.merge(eventId, 1, Integer::sum);
        }
    }

    private static final class FakeBroker implements EventPublisher {
        final List<UUID> confirmed = new ArrayList<>();
        Set<UUID> refused = Set.of();

        @Override
        public void publish(OutboxMessage message) {
            if (refused.contains(message.eventId())) {
                throw new EventPublishException("not confirmed: " + message.eventId(), null);
            }
            confirmed.add(message.eventId());
        }
    }

    private final FakeOutbox outbox = new FakeOutbox();
    private final FakeBroker broker = new FakeBroker();
    private final PublishPendingEventsService relay =
            new PublishPendingEventsService(outbox, broker, Clock.fixed(NOW, ZoneOffset.UTC), 50);

    private UUID pending(String routingKey) {
        UUID id = UUID.randomUUID();
        outbox.pending.put(id, new OutboxMessage(id, routingKey, "{\"eventId\":\"" + id + "\"}"));
        return id;
    }

    @Test
    void everyPendingEventIsPublishedInOrderAndMarkedAfterItsConfirm() {
        UUID first = pending("transactions.transaction-confirmed");
        UUID second = pending("transactions.transaction-failed");

        assertThat(relay.publishPending()).isEqualTo(2);
        assertThat(broker.confirmed).containsExactly(first, second);
        assertThat(outbox.published).containsEntry(first, NOW).containsEntry(second, NOW);
        assertThat(outbox.askedLimit).isEqualTo(50);
    }

    @Test
    void theFirstRefusalStopsTheRunCountsTheAttemptAndKeepsTheRestPending() {
        UUID first = pending("transactions.transaction-confirmed");
        UUID second = pending("transactions.transaction-confirmed");
        UUID third = pending("transactions.transaction-failed");
        broker.refused = Set.of(second);

        assertThat(relay.publishPending()).isEqualTo(1);
        assertThat(outbox.published).containsOnlyKeys(first);
        assertThat(outbox.attempts).containsExactly(Map.entry(second, 1));
        assertThat(broker.confirmed).doesNotContain(third);
    }

    @Test
    void aRefusedEventIsRetriedInTheNextRun() {
        UUID event = pending("transactions.transaction-confirmed");
        broker.refused = Set.of(event);
        relay.publishPending();
        broker.refused = Set.of();

        assertThat(relay.publishPending()).isEqualTo(1);
        assertThat(outbox.published).containsKey(event);
        assertThat(outbox.attempts).containsEntry(event, 1);
    }

    @Test
    void anEmptyOutboxPublishesNothing() {
        assertThat(relay.publishPending()).isZero();
        assertThat(broker.confirmed).isEmpty();
    }
}

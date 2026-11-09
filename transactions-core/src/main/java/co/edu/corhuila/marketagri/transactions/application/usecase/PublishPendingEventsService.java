package co.edu.corhuila.marketagri.transactions.application.usecase;

import co.edu.corhuila.marketagri.transactions.application.port.in.PublishPendingEvents;
import co.edu.corhuila.marketagri.transactions.application.port.out.EventPublisher;
import co.edu.corhuila.marketagri.transactions.application.port.out.Outbox;
import co.edu.corhuila.marketagri.transactions.domain.model.EventPublishException;
import co.edu.corhuila.marketagri.transactions.domain.model.OutboxMessage;
import java.time.Clock;

/**
 * The outbox relay (ADR-014): oldest first, an event is marked published only after the broker
 * confirmed it. The first refusal counts an attempt and stops the run (the publisher logs why), so
 * the next run starts again from the same event.
 * A crash between the confirm and the mark publishes it twice: delivery is at-least-once, and every
 * consumer is idempotent on {@code eventId} (D-C18).
 */
public final class PublishPendingEventsService implements PublishPendingEvents {

    private final Outbox outbox;
    private final EventPublisher publisher;
    private final Clock clock;
    private final int batchSize;

    public PublishPendingEventsService(Outbox outbox, EventPublisher publisher, Clock clock, int batchSize) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Override
    public int publishPending() {
        int published = 0;
        for (OutboxMessage message : outbox.pending(batchSize)) {
            try {
                publisher.publish(message);
            } catch (EventPublishException refused) {
                outbox.recordFailedAttempt(message.eventId());
                break;
            }
            outbox.markPublished(message.eventId(), clock.instant());
            published++;
        }
        return published;
    }
}

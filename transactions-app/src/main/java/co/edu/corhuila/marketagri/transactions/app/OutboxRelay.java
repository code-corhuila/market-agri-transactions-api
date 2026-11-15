package co.edu.corhuila.marketagri.transactions.app;

import co.edu.corhuila.marketagri.transactions.application.port.in.PublishPendingEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the outbox relay every {@code outbox.relay.interval} (ADR-014), one run at a time. Turned
 * off with {@code outbox.relay.enabled=false} (the tests without a broker).
 */
@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);

    private final PublishPendingEvents relay;

    public OutboxRelay(PublishPendingEvents relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval}")
    public void run() {
        try {
            int published = relay.publishPending();
            if (published > 0) {
                LOG.info("Outbox relay published {} events", published);
            }
        } catch (RuntimeException failure) {
            // A database outage must not stop the scheduler: the next run tries again.
            LOG.warn("Outbox relay run failed: {}", failure.toString());
        }
    }
}

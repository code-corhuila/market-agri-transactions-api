package co.edu.corhuila.marketagri.transactions.application.port.out;

import co.edu.corhuila.marketagri.transactions.domain.model.EventPublishException;
import co.edu.corhuila.marketagri.transactions.domain.model.OutboxMessage;

/** The broker of the domain events (ADR-004, ADR-014). */
public interface EventPublisher {

    /**
     * Returns only once the broker confirmed the message.
     *
     * @throws EventPublishException when it did not
     */
    void publish(OutboxMessage message);
}

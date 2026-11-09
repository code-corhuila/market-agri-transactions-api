package co.edu.corhuila.marketagri.transactions.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.transactions.domain.model.EventPublishException;
import co.edu.corhuila.marketagri.transactions.domain.model.OutboxMessage;
import com.rabbitmq.client.AMQP;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The publisher of the outbox relay on RabbitMQ (ADR-014): persistent JSON messages with the event
 * id, on market-agri.events; without a broker it reports DOWN and refuses quickly, never hangs.
 */
class RabbitEventPublisherTest {

    private static final OutboxMessage MESSAGE = new OutboxMessage(
            UUID.fromString("e0000000-0000-4000-8000-000000000001"), "transactions.transaction-confirmed", "{}");

    /** Nobody listens on port 1. */
    private final RabbitEventPublisher unreachable =
            new RabbitEventPublisher("localhost", 1, "transactions_app", "unused", Duration.ofSeconds(2));

    @Test
    void theExchangeIsTheOneInfraDeclares() {
        assertThat(RabbitEventPublisher.EXCHANGE).isEqualTo("market-agri.events");
    }

    @Test
    void aMessageIsPersistentJsonWithTheEventId() {
        AMQP.BasicProperties properties = RabbitEventPublisher.properties(MESSAGE);

        assertThat(properties.getDeliveryMode()).isEqualTo(2);
        assertThat(properties.getContentType()).isEqualTo("application/json");
        assertThat(properties.getMessageId()).isEqualTo(MESSAGE.eventId().toString());
    }

    @Test
    void withoutABrokerItIsDownAndRefusesQuickly() {
        Instant start = Instant.now();

        assertThat(unreachable.name()).isEqualTo("broker");
        assertThat(unreachable.isUp()).isFalse();
        assertThatThrownBy(() -> unreachable.publish(MESSAGE)).isInstanceOf(EventPublishException.class);
        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(10));
    }
}

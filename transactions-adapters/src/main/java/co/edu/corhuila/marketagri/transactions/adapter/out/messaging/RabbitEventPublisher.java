package co.edu.corhuila.marketagri.transactions.adapter.out.messaging;

import co.edu.corhuila.marketagri.transactions.application.port.out.EventPublisher;
import co.edu.corhuila.marketagri.transactions.application.port.out.ReadinessCheck;
import co.edu.corhuila.marketagri.transactions.domain.model.EventPublishException;
import co.edu.corhuila.marketagri.transactions.domain.model.OutboxMessage;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes the outbox to {@code market-agri.events} with publisher confirms, with the RabbitMQ
 * Java client and no framework on top: nothing is ever declared, the topology belongs to infra and
 * {@code transactions_app} may only write to that exchange (ADR-014). One connection, opened on
 * demand and opened again after a failure; it is also the {@code broker} check of E-61.
 */
public final class RabbitEventPublisher implements EventPublisher, ReadinessCheck, AutoCloseable {

    static final String EXCHANGE = "market-agri.events";
    private static final Logger LOG = LoggerFactory.getLogger(RabbitEventPublisher.class);
    private static final String NAME = "broker";
    private static final String JSON = "application/json";
    private static final int PERSISTENT = 2;

    private final ConnectionFactory factory = new ConnectionFactory();
    private final Duration confirmTimeout;
    private Connection connection;

    public RabbitEventPublisher(String host, int port, String username, String password, Duration timeout) {
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setConnectionTimeout((int) timeout.toMillis());
        factory.setHandshakeTimeout((int) timeout.toMillis());
        factory.setAutomaticRecoveryEnabled(false);
        this.confirmTimeout = timeout;
    }

    static AMQP.BasicProperties properties(OutboxMessage message) {
        return new AMQP.BasicProperties.Builder()
                .contentType(JSON)
                .deliveryMode(PERSISTENT)
                .messageId(message.eventId().toString())
                .build();
    }

    @Override
    public void publish(OutboxMessage message) {
        try (Channel channel = connection().createChannel()) {
            channel.confirmSelect();
            channel.basicPublish(EXCHANGE, message.routingKey(), properties(message),
                    message.envelope().getBytes(StandardCharsets.UTF_8));
            channel.waitForConfirmsOrDie(confirmTimeout.toMillis());
        } catch (IOException | TimeoutException | InterruptedException | RuntimeException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warn("Event {} not confirmed by the broker, it stays in the outbox: {}", message.eventId(),
                    failure.toString());
            dropConnection();
            throw new EventPublishException("not confirmed: " + message.eventId(), failure);
        }
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isUp() {
        try {
            return connection().isOpen();
        } catch (IOException | TimeoutException | RuntimeException failure) {
            LOG.warn("Broker not ready: {}", failure.toString());
            return false;
        }
    }

    @Override
    public synchronized void close() {
        dropConnection();
    }

    private synchronized Connection connection() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            connection = factory.newConnection("transactions-api outbox relay");
        }
        return connection;
    }

    private synchronized void dropConnection() {
        if (connection != null) {
            try {
                connection.abort();
            } finally {
                connection = null;
            }
        }
    }
}

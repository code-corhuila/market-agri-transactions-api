package co.edu.corhuila.marketagri.transactions.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.marketagri.transactions.application.port.out.Outbox;
import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentSettlements;
import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionCreations;
import co.edu.corhuila.marketagri.transactions.domain.model.FailureReason;
import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.OutboxMessage;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentMethod;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentOutcome;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentResult;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Quantity;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The relay's SQL against the real outbox_event (ADR-014); every test rolls back (no DELETE). */
@SpringBootTest
class JdbcOutboxIT extends TransactionsIntegrationTest {

    @Autowired
    private Outbox outbox;

    @Autowired
    private PaymentSettlements settlements;

    @Autowired
    private TransactionCreations creations;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private void inRolledBackTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            work.run();
        });
    }

    /** A failed payment and its TransactionFailed row, as E-43 leaves them. */
    private UUID failedPaymentEvent() {
        PurchaseSnapshot purchase = new PurchaseSnapshot(UUID.randomUUID(), UUID.randomUUID(), "Carlos Pérez",
                UUID.randomUUID(), "Ana Ruiz", UUID.randomUUID(), "Café pergamino seco", Quantity.of("1"),
                ProductUnit.ARROBA, Money.ofCents(680_000));
        Transaction payment = Transaction.open(UUID.randomUUID(), purchase, Instant.now());
        creations.add(payment);
        Transaction failed = payment.fail(new PaymentResult("w-" + UUID.randomUUID(), PaymentOutcome.REJECTED,
                680_000, "COP", PaymentMethod.CARD), Instant.now());
        UUID eventId = UUID.randomUUID();
        settlements.settle(failed, TransactionEvent.failed(eventId, failed, FailureReason.PAYMENT_FAILED, "c", "w"));
        return eventId;
    }

    @Test
    void pendingRowsComeWithTheirRoutingKeyAndEnvelopeUntilPublished() {
        inRolledBackTransaction(() -> {
            UUID eventId = failedPaymentEvent();

            List<OutboxMessage> pending = outbox.pending(500);
            assertThat(pending).anySatisfy(message -> {
                assertThat(message.eventId()).isEqualTo(eventId);
                assertThat(message.routingKey()).isEqualTo("transactions.transaction-failed");
                assertThat(message.envelope()).contains("\"eventId\"").contains(eventId.toString());
            });

            outbox.markPublished(eventId, Instant.now());
            assertThat(outbox.pending(500)).noneMatch(message -> message.eventId().equals(eventId));
        });
    }

    @Test
    void aFailedAttemptIsCounted() {
        inRolledBackTransaction(() -> {
            UUID eventId = failedPaymentEvent();

            outbox.recordFailedAttempt(eventId);
            outbox.recordFailedAttempt(eventId);

            assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_event WHERE event_id = ?", Integer.class, eventId))
                    .isEqualTo(2);
        });
    }
}

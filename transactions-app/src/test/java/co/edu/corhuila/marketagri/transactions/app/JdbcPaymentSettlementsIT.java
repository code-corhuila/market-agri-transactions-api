package co.edu.corhuila.marketagri.transactions.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentSettlements;
import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionCreations;
import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentMethod;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentOutcome;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentResult;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Quantity;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The SQL of {@code PaymentSettlements} against the real tables of transactions-db: the new state
 * and its outbox row in the same transaction. transactions_app has no DELETE, so every test rolls back.
 */
@SpringBootTest
class JdbcPaymentSettlementsIT extends TransactionsIntegrationTest {

    private static final Instant NOW = Instant.now();

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

    private static Transaction pending() {
        PurchaseSnapshot purchase = new PurchaseSnapshot(UUID.randomUUID(), UUID.randomUUID(), "Carlos Pérez",
                UUID.randomUUID(), "Ana Ruiz", UUID.randomUUID(), "Café pergamino seco", Quantity.of("12.5"),
                ProductUnit.KILOGRAMO, Money.ofCents(680_000));
        return Transaction.open(UUID.randomUUID(), purchase, NOW);
    }

    private static PaymentResult approved(String wompiId) {
        return new PaymentResult(wompiId, PaymentOutcome.APPROVED, 8_500_000, "COP", PaymentMethod.NEQUI);
    }

    @Test
    void theNewStateAndItsEventAreStoredTogether() {
        Transaction payment = pending();
        String wompiId = "w-" + UUID.randomUUID();

        inRolledBackTransaction(() -> {
            creations.add(payment);
            Transaction locked = settlements.lockById(payment.id()).orElseThrow();
            Transaction confirmed = locked.confirm(approved(wompiId), NOW.plusSeconds(60));
            UUID eventId = UUID.randomUUID();
            settlements.settle(confirmed, TransactionEvent.confirmed(eventId, confirmed, "corr-1", wompiId));

            assertThat(settlements.lockById(payment.id())).contains(confirmed);
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT event_type, routing_key, envelope->>'eventId' AS envelope_id, published_at, attempts"
                            + " FROM outbox_event WHERE event_id = ?", eventId);
            assertThat(row).containsEntry("event_type", "TransactionConfirmed")
                    .containsEntry("routing_key", "transactions.transaction-confirmed")
                    .containsEntry("envelope_id", eventId.toString())
                    .containsEntry("attempts", 0);
            assertThat(row.get("published_at")).isNull();
        });
    }

    @Test
    void aWompiIdOfAnotherPaymentIsTaken() {
        Transaction first = pending();
        Transaction second = pending();
        String wompiId = "w-" + UUID.randomUUID();

        inRolledBackTransaction(() -> {
            creations.add(first);
            creations.add(second);
            Transaction confirmed = first.confirm(approved(wompiId), NOW.plusSeconds(60));
            settlements.settle(confirmed, TransactionEvent.confirmed(UUID.randomUUID(), confirmed, "c", wompiId));

            assertThat(settlements.gatewayTransactionIdTakenByAnother(wompiId, second.id())).isTrue();
            assertThat(settlements.gatewayTransactionIdTakenByAnother(wompiId, first.id())).isFalse();
            assertThat(settlements.lockById(UUID.randomUUID())).isEmpty();
        });
    }
}

package co.edu.corhuila.marketagri.transactions.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionCreations;
import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Quantity;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionInProgressException;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The SQL of {@code TransactionCreations} against the real tables of transactions-db (B2, D-21).
 * transactions_app has no DELETE, so each test runs in a transaction that is rolled back.
 */
@SpringBootTest
class JdbcTransactionCreationsIT extends TransactionsIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-04T15:00:00.123456789Z");
    private static final UUID SAGA = UUID.fromString("5a6a0000-0000-4000-8000-000000000001");

    @Autowired
    private TransactionCreations creations;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private void inRolledBackTransaction(Consumer<TransactionCreations> work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            work.accept(creations);
        });
    }

    private static PurchaseSnapshot purchase(UUID conversation, String quantity) {
        return new PurchaseSnapshot(conversation, UUID.randomUUID(), "Carlos Pérez", UUID.randomUUID(), "Ana Ruiz",
                UUID.randomUUID(), "Café pergamino seco", Quantity.of(quantity), ProductUnit.ARROBA,
                Money.ofCents(680_000));
    }

    private static Transaction pending(UUID conversation) {
        return Transaction.open(UUID.randomUUID(), purchase(conversation, "12.5"), NOW);
    }

    @Test
    void aStoredPaymentComesBackEqualThroughItsKey() {
        Transaction payment = pending(UUID.randomUUID());

        inRolledBackTransaction(store -> {
            assertThat(store.add(payment)).isTrue();
            assertThat(store.claimKey(SAGA, payment.id().toString(), payment.id())).isTrue();

            assertThat(store.transactionForKey(SAGA, payment.id().toString())).contains(payment);
        });
    }

    @Test
    void aPaymentThatFailedToOpenComesBackFailedAtItsCreation() {
        Transaction failed = pending(UUID.randomUUID()).failedToOpen();

        inRolledBackTransaction(store -> {
            store.add(failed);
            store.claimKey(SAGA, failed.id().toString(), failed.id());

            Transaction stored = store.transactionForKey(SAGA, failed.id().toString()).orElseThrow();
            assertThat(stored).isEqualTo(failed);
            assertThat(stored.neverOpened()).isTrue();
        });
    }

    @Test
    void theSameIdIsWrittenOnlyOnce() {
        Transaction payment = pending(UUID.randomUUID());

        inRolledBackTransaction(store -> {
            assertThat(store.add(payment)).isTrue();
            assertThat(store.add(payment)).isFalse();
        });
    }

    @Test
    void aSecondPendingPaymentForTheConversationIsInProgress() {
        UUID conversation = UUID.randomUUID();

        inRolledBackTransaction(store -> {
            store.add(pending(conversation));

            assertThatThrownBy(() -> store.add(pending(conversation)))
                    .isInstanceOf(TransactionInProgressException.class);
        });
    }

    @Test
    void aFailedPaymentDoesNotBlockTheConversation() {
        UUID conversation = UUID.randomUUID();

        inRolledBackTransaction(store -> {
            store.add(pending(conversation).failedToOpen());

            assertThat(store.add(pending(conversation))).isTrue();
        });
    }

    @Test
    void aKeyIsClaimedOncePerCaller() {
        Transaction payment = pending(UUID.randomUUID());
        String key = payment.id().toString();

        inRolledBackTransaction(store -> {
            store.add(payment);
            assertThat(store.claimKey(SAGA, key, payment.id())).isTrue();
            assertThat(store.claimKey(SAGA, key, payment.id())).isFalse();
            assertThat(store.claimKey(UUID.randomUUID(), key, payment.id())).isTrue();
            assertThat(store.transactionForKey(SAGA, "unknown-key")).isEmpty();
        });
    }
}

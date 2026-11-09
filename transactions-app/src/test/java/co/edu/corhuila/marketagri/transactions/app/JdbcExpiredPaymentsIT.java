package co.edu.corhuila.marketagri.transactions.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.marketagri.transactions.application.port.out.ExpiredPayments;
import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionCreations;
import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Quantity;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The expiry job's query against the real table: PENDING payments whose checkout ended before the cutoff. */
@SpringBootTest
class JdbcExpiredPaymentsIT extends TransactionsIntegrationTest {

    @Autowired
    private ExpiredPayments expired;

    @Autowired
    private TransactionCreations creations;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static Transaction pendingCreatedAt(Instant createdAt) {
        PurchaseSnapshot purchase = new PurchaseSnapshot(UUID.randomUUID(), UUID.randomUUID(), "Carlos Pérez",
                UUID.randomUUID(), "Ana Ruiz", UUID.randomUUID(), "Café pergamino seco", Quantity.of("1"),
                ProductUnit.ARROBA, Money.ofCents(680_000));
        return Transaction.open(UUID.randomUUID(), purchase, createdAt);
    }

    @Test
    void onlyPendingPaymentsThatExpiredBeforeTheCutoffAreListed() {
        Instant cutoff = Instant.parse("2026-11-08T21:00:00Z");
        Transaction old = pendingCreatedAt(cutoff.minus(Transaction.CHECKOUT_WINDOW).minusSeconds(60));
        Transaction recent = pendingCreatedAt(cutoff.minus(Transaction.CHECKOUT_WINDOW).plusSeconds(60));
        Transaction oldButFailed = pendingCreatedAt(cutoff.minusSeconds(7200)).failedToOpen();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            creations.add(old);
            creations.add(recent);
            creations.add(oldButFailed);

            assertThat(expired.pendingExpiredBefore(cutoff, 1000)).contains(old.id())
                    .doesNotContain(recent.id(), oldButFailed.id());
        });
    }
}

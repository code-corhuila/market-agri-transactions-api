package co.edu.corhuila.marketagri.transactions.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionCreations;
import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionRepository;
import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.AdminTransactionFilter;
import co.edu.corhuila.marketagri.transactions.domain.model.Page;
import co.edu.corhuila.marketagri.transactions.domain.model.PageRequest;
import co.edu.corhuila.marketagri.transactions.domain.model.Participant;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Quantity;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionFilter;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The SQL of E-41 and E-42 against the real table (B2), checked against {@code TransactionFilter#matches}:
 * side, status, order and pages. transactions_app has no DELETE, so it runs in a rolled-back transaction.
 */
@SpringBootTest
class JdbcTransactionRepositoryIT extends TransactionsIntegrationTest {

    @Autowired
    private TransactionCreations creations;

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static Transaction open(UUID buyer, UUID producer, String createdAt) {
        return Transaction.open(UUID.randomUUID(), new PurchaseSnapshot(UUID.randomUUID(), buyer,
                "Carlos Pérez", producer, "Ana Ruiz", UUID.randomUUID(), "Café pergamino seco", Quantity.of("12.5"),
                ProductUnit.ARROBA, Money.ofCents(680_000)), Instant.parse(createdAt));
    }

    private Transaction add(Transaction transaction) {
        creations.add(transaction);
        return transaction;
    }

    @Test
    void readsOneAndListsEachSideNewestFirstWithItsTotal() {
        UUID buyer = UUID.randomUUID();
        UUID producer = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            Transaction failed = add(open(buyer, producer, "2026-11-04T14:00:00Z").failedToOpen());
            Transaction older = add(open(buyer, producer, "2026-11-04T15:00:00Z"));
            Transaction newer = add(open(buyer, producer, "2026-11-04T16:00:00Z").failedToOpen());
            Transaction otherBuyer = add(open(UUID.randomUUID(), producer, "2026-11-04T17:00:00Z"));

            assertThat(repository.findById(newer.id())).contains(newer);
            assertThat(repository.findById(UUID.randomUUID())).isEmpty();

            Page<Transaction> first = repository.findPage(new TransactionFilter(Participant.BUYER, buyer, null),
                    new PageRequest(1, 1));
            assertThat(first.items()).containsExactly(newer);
            assertThat(first.total()).isEqualTo(3);
            assertThat(repository.findPage(new TransactionFilter(Participant.BUYER, buyer, null),
                    new PageRequest(2, 1)).items()).containsExactly(older);
            assertThat(repository.findPage(new TransactionFilter(Participant.PRODUCER, producer, null),
                    new PageRequest(1, 20)).items().get(0)).isEqualTo(otherBuyer);
            assertThat(repository.findPage(new TransactionFilter(Participant.BUYER, buyer, TransactionStatus.FAILED),
                    new PageRequest(1, 20)).items()).containsExactly(newer, failed);
            assertThat(repository.findPage(new TransactionFilter(Participant.PRODUCER, buyer, null),
                    new PageRequest(1, 20)).total()).isZero();
        });
    }

    @Test
    void adminFiltersByStatusProducerAndBuyerWithTheContractOrder() {
        UUID buyer = UUID.randomUUID();
        UUID producer = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            Transaction older = add(open(buyer, producer, "2026-11-04T15:00:00Z"));
            Transaction newer = add(open(buyer, producer, "2026-11-04T16:00:00Z").failedToOpen());
            add(open(UUID.randomUUID(), producer, "2026-11-04T17:00:00Z"));

            Page<Transaction> page = repository.findPage(new AdminTransactionFilter(null, producer, buyer),
                    new PageRequest(1, 20));
            assertThat(page.items()).containsExactly(newer, older);
            assertThat(page.total()).isEqualTo(2);
            assertThat(repository.findPage(new AdminTransactionFilter(TransactionStatus.FAILED, null, buyer),
                    new PageRequest(1, 20)).items()).containsExactly(newer);
        });
    }
}

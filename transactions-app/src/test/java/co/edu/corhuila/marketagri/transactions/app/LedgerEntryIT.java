package co.edu.corhuila.marketagri.transactions.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.transactions.application.port.in.ApplyPaymentResult;
import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionCreations;
import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentMethod;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentOutcome;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentResult;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Quantity;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The confirmation with its ledger entry against the real tables (AGGR-INV-006), through the use
 * case as the composition root wires it. transactions_app has no DELETE: every test rolls back,
 * except the atomicity one, which leaves one PENDING payment in the throwaway database.
 */
@SpringBootTest
class LedgerEntryIT extends TransactionsIntegrationTest {

    @Autowired
    private ApplyPaymentResult applyPaymentResult;

    @Autowired
    private TransactionCreations creations;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final UUID producer = UUID.randomUUID();

    private void inRolledBackTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            work.run();
        });
    }

    private Transaction pending() {
        PurchaseSnapshot purchase = new PurchaseSnapshot(UUID.randomUUID(), UUID.randomUUID(), "Carlos Pérez",
                producer, "Ana Ruiz", UUID.randomUUID(), "Café pergamino seco", Quantity.of("12.5"),
                ProductUnit.KILOGRAMO, Money.ofCents(680_000));
        return Transaction.open(UUID.randomUUID(), purchase, Instant.now());
    }

    private void confirm(Transaction payment, String wompiId) {
        applyPaymentResult.apply(new ApplyPaymentResult.Command(payment.id(),
                new PaymentResult(wompiId, PaymentOutcome.APPROVED, 8_500_000, "COP", PaymentMethod.NEQUI), "corr-1"));
    }

    private Map<String, Object> entryOf(UUID transactionId) {
        return jdbc.queryForMap("SELECT producer_id, gross_amount_cents, platform_fee_cents, net_amount_cents, status,"
                + " created_at FROM ledger_entry WHERE transaction_id = ?", transactionId);
    }

    @Test
    void aConfirmationLeavesOneEntryForTheProducerWithTheWholeAmountAtTheConfirmationTime() {
        Transaction payment = pending();

        inRolledBackTransaction(() -> {
            creations.add(payment);
            confirm(payment, "w-" + UUID.randomUUID());
            confirm(payment, "w-" + UUID.randomUUID());

            Map<String, Object> entry = entryOf(payment.id());
            assertThat(entry).containsEntry("producer_id", producer)
                    .containsEntry("gross_amount_cents", 8_500_000L)
                    .containsEntry("platform_fee_cents", 0L)
                    .containsEntry("net_amount_cents", 8_500_000L)
                    .containsEntry("status", "PENDING_DISBURSEMENT");
            OffsetDateTime confirmedAt = jdbc.queryForObject(
                    "SELECT confirmed_at FROM payment_transaction WHERE id = ?", OffsetDateTime.class, payment.id());
            assertThat(jdbc.queryForObject("SELECT created_at = ? FROM ledger_entry WHERE transaction_id = ?",
                    Boolean.class, confirmedAt, payment.id())).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entry WHERE transaction_id = ?", Long.class,
                    payment.id())).isEqualTo(1);
        });
    }

    @Test
    void aFailedPaymentHasNoEntry() {
        Transaction payment = pending();

        inRolledBackTransaction(() -> {
            creations.add(payment);
            applyPaymentResult.apply(new ApplyPaymentResult.Command(payment.id(), new PaymentResult("w-x",
                    PaymentOutcome.REJECTED, 8_500_000, "COP", PaymentMethod.CARD), "corr-1"));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entry WHERE transaction_id = ?", Long.class,
                    payment.id())).isZero();
        });
    }

    /**
     * Committed on purpose: a refused entry rolls back the confirmation, and that has to be read from
     * a new transaction. An entry already there for the payment makes the unique index refuse the new one.
     */
    @Test
    void whenTheEntryIsRefusedTheConfirmationAndItsEventAreRolledBackToo() {
        Transaction payment = pending();
        creations.add(payment);
        jdbc.update("INSERT INTO ledger_entry (transaction_id, producer_id, gross_amount_cents, platform_fee_cents,"
                + " net_amount_cents) VALUES (?, ?, 1, 0, 1)", payment.id(), producer);

        assertThatThrownBy(() -> confirm(payment, "w-" + UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT status FROM payment_transaction WHERE id = ?", String.class,
                payment.id())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE envelope::text LIKE ?", Long.class,
                "%" + payment.id() + "%")).isZero();
    }
}

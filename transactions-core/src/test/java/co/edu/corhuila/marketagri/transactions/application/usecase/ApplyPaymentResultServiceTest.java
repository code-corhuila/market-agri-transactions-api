package co.edu.corhuila.marketagri.transactions.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.marketagri.transactions.application.port.in.ApplyPaymentResult.Command;
import co.edu.corhuila.marketagri.transactions.application.port.in.ApplyPaymentResult.Outcome;
import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentSettlements;
import co.edu.corhuila.marketagri.transactions.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.transactions.domain.model.FailureReason;
import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentMethod;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentOutcome;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentResult;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Quantity;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionEvent;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * A verified result of the provider in the core (E-43, ADR-014, ADR-019): the state change and its
 * event in one transaction, and every other case changes nothing (the webhook still answers 200).
 */
class ApplyPaymentResultServiceTest {

    private static final Instant CREATED = Instant.parse("2026-11-04T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-04T15:07:30.5Z");
    private static final UUID TX = UUID.fromString("7a1f0c2e-0000-4000-8000-000000000001");
    private static final UUID OTHER_TX = UUID.fromString("7a1f0c2e-0000-4000-8000-000000000002");
    private static final UUID EVENT = UUID.fromString("e0000000-0000-4000-8000-000000000001");
    private static final long AMOUNT = 8_500_000;

    /** Commits what the work wrote, or throws it away when the work throws (like a rollback). */
    private static final class FakeStore implements UnitOfWork, PaymentSettlements {
        final Map<UUID, Transaction> transactions = new LinkedHashMap<>();
        final List<TransactionEvent> outbox = new ArrayList<>();
        int units;
        private boolean open;

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            Map<UUID, Transaction> before = new LinkedHashMap<>(transactions);
            List<TransactionEvent> outboxBefore = new ArrayList<>(outbox);
            open = true;
            units++;
            try {
                return work.get();
            } catch (RuntimeException rollback) {
                transactions.clear();
                transactions.putAll(before);
                outbox.clear();
                outbox.addAll(outboxBefore);
                throw rollback;
            } finally {
                open = false;
            }
        }

        @Override
        public Optional<Transaction> lockById(UUID id) {
            assertThat(open).as("lock inside the unit of work").isTrue();
            return Optional.ofNullable(transactions.get(id));
        }

        @Override
        public boolean gatewayTransactionIdTakenByAnother(String gatewayTransactionId, UUID transactionId) {
            return transactions.values().stream().anyMatch(stored -> !stored.id().equals(transactionId)
                    && gatewayTransactionId.equals(stored.gatewayTransactionId()));
        }

        @Override
        public void settle(Transaction settled, TransactionEvent event) {
            assertThat(open).as("settle inside the unit of work").isTrue();
            transactions.put(settled.id(), settled);
            outbox.add(event);
        }
    }

    private final FakeStore store = new FakeStore();
    private final ApplyPaymentResultService service =
            new ApplyPaymentResultService(store, store, () -> EVENT, Clock.fixed(NOW, ZoneOffset.UTC));

    private static Transaction pending(UUID id) {
        PurchaseSnapshot purchase = new PurchaseSnapshot(UUID.randomUUID(), UUID.randomUUID(), "Carlos Pérez",
                UUID.randomUUID(), "Ana Ruiz", UUID.randomUUID(), "Café pergamino seco",
                Quantity.of("1"), ProductUnit.ARROBA, Money.ofCents(AMOUNT));
        return Transaction.open(id, purchase, CREATED);
    }

    private static Command command(UUID id, PaymentOutcome outcome, long amountCents, String gatewayId) {
        return new Command(id, new PaymentResult(gatewayId, outcome, amountCents, "COP", PaymentMethod.CARD), "corr-1");
    }

    @Test
    void anApprovedResultConfirmsAndLeavesTransactionConfirmedInTheOutbox() {
        store.transactions.put(TX, pending(TX));

        Outcome outcome = service.apply(command(TX, PaymentOutcome.APPROVED, AMOUNT, "w-1"));

        assertThat(outcome).isEqualTo(Outcome.CONFIRMED);
        assertThat(store.transactions.get(TX).status()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(store.outbox).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isEqualTo(EVENT);
            assertThat(event.type()).isEqualTo(TransactionEvent.Type.TRANSACTION_CONFIRMED);
            assertThat(event.transaction()).isEqualTo(store.transactions.get(TX));
            assertThat(event.correlationId()).isEqualTo("corr-1");
            assertThat(event.causationId()).isEqualTo("w-1");
        });
    }

    @Test
    void aRejectedResultFailsAndLeavesTransactionFailedForThePayment() {
        store.transactions.put(TX, pending(TX));

        Outcome outcome = service.apply(command(TX, PaymentOutcome.REJECTED, AMOUNT, "w-1"));

        assertThat(outcome).isEqualTo(Outcome.FAILED);
        assertThat(store.transactions.get(TX).status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(store.outbox).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(TransactionEvent.Type.TRANSACTION_FAILED);
            assertThat(event.reason()).isEqualTo(FailureReason.PAYMENT_FAILED);
        });
    }

    @Test
    void aPendingResultChangesNothingAndOpensNoTransaction() {
        store.transactions.put(TX, pending(TX));

        assertThat(service.apply(command(TX, PaymentOutcome.PENDING, AMOUNT, "w-1"))).isEqualTo(Outcome.STILL_PENDING);
        assertThat(store.transactions.get(TX).status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(store.outbox).isEmpty();
        assertThat(store.units).isZero();
    }

    @Test
    void anUnknownTransactionChangesNothing() {
        assertThat(service.apply(command(TX, PaymentOutcome.APPROVED, AMOUNT, "w-1")))
                .isEqualTo(Outcome.UNKNOWN_TRANSACTION);
        assertThat(store.outbox).isEmpty();
    }

    @Test
    void aRedeliveredResultChangesNothing() {
        store.transactions.put(TX, pending(TX));
        service.apply(command(TX, PaymentOutcome.APPROVED, AMOUNT, "w-1"));
        Transaction confirmed = store.transactions.get(TX);

        assertThat(service.apply(command(TX, PaymentOutcome.APPROVED, AMOUNT, "w-1"))).isEqualTo(Outcome.ALREADY_SETTLED);
        assertThat(service.apply(command(TX, PaymentOutcome.REJECTED, AMOUNT, "w-1"))).isEqualTo(Outcome.ALREADY_SETTLED);
        assertThat(store.transactions.get(TX)).isEqualTo(confirmed);
        assertThat(store.outbox).hasSize(1);
    }

    @Test
    void anotherAmountIsRefusedByTheGuardAndTheTransactionStaysPending() {
        store.transactions.put(TX, pending(TX));

        assertThat(service.apply(command(TX, PaymentOutcome.APPROVED, AMOUNT + 100, "w-1")))
                .isEqualTo(Outcome.REFUSED_BY_GUARD);
        assertThat(store.transactions.get(TX).status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(store.outbox).isEmpty();
    }

    @Test
    void aProviderPaymentAlreadyUsedByAnotherTransactionIsRefusedByTheGuard() {
        store.transactions.put(OTHER_TX, pending(OTHER_TX));
        service.apply(command(OTHER_TX, PaymentOutcome.APPROVED, AMOUNT, "w-1"));
        store.transactions.put(TX, pending(TX));

        assertThat(service.apply(command(TX, PaymentOutcome.APPROVED, AMOUNT, "w-1"))).isEqualTo(Outcome.REFUSED_BY_GUARD);
        assertThat(service.apply(command(TX, PaymentOutcome.REJECTED, AMOUNT, "w-1"))).isEqualTo(Outcome.REFUSED_BY_GUARD);
        assertThat(store.transactions.get(TX).status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(store.outbox).hasSize(1);
    }
}

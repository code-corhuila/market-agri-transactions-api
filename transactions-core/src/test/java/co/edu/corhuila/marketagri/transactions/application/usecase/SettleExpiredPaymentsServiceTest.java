package co.edu.corhuila.marketagri.transactions.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.marketagri.transactions.application.port.in.ApplyPaymentResult;
import co.edu.corhuila.marketagri.transactions.application.port.out.ExpiredPayments;
import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentLookup;
import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentSettlements;
import co.edu.corhuila.marketagri.transactions.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.transactions.domain.model.FailureReason;
import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentLookupException;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * The expiry job (E-43, FR-025 amended 2026-11-04): a payment still PENDING 5 minutes after its
 * checkout expired is checked with the provider by reference; an approved or rejected result is
 * applied as the webhook would; with no payment it fails EXPIRED; with no answer it waits.
 */
class SettleExpiredPaymentsServiceTest {

    private static final Instant CREATED = Instant.parse("2026-11-08T20:00:00Z");
    private static final Instant NOW = CREATED.plus(Transaction.CHECKOUT_WINDOW).plus(Duration.ofMinutes(6));
    private static final long AMOUNT = 8_500_000;

    private static final class Store implements UnitOfWork, PaymentSettlements, ExpiredPayments, PaymentLookup {
        final Map<UUID, Transaction> transactions = new LinkedHashMap<>();
        final List<TransactionEvent> outbox = new ArrayList<>();
        final Map<UUID, PaymentResult> provider = new HashMap<>();
        Set<UUID> unanswered = Set.of();
        Instant askedCutoff;

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }

        @Override
        public Optional<Transaction> lockById(UUID id) {
            return Optional.ofNullable(transactions.get(id));
        }

        @Override
        public boolean gatewayTransactionIdTakenByAnother(String gatewayTransactionId, UUID transactionId) {
            return false;
        }

        @Override
        public void settle(Transaction settled, TransactionEvent event) {
            transactions.put(settled.id(), settled);
            outbox.add(event);
        }

        @Override
        public List<UUID> pendingExpiredBefore(Instant cutoff, int limit) {
            askedCutoff = cutoff;
            return transactions.values().stream().filter(Transaction::isPending)
                    .filter(transaction -> transaction.expiresAt().isBefore(cutoff)).map(Transaction::id).toList();
        }

        @Override
        public Optional<PaymentResult> findByReference(UUID transactionId) {
            if (unanswered.contains(transactionId)) {
                throw new PaymentLookupException("no answer", null);
            }
            return Optional.ofNullable(provider.get(transactionId));
        }
    }

    private final Store store = new Store();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ApplyPaymentResult apply = new ApplyPaymentResultService(store, store, UUID::randomUUID, clock);
    private final SettleExpiredPaymentsService job =
            new SettleExpiredPaymentsService(store, store, apply, clock, 100);

    private UUID pending() {
        PurchaseSnapshot purchase = new PurchaseSnapshot(UUID.randomUUID(), UUID.randomUUID(), "Carlos Pérez",
                UUID.randomUUID(), "Ana Ruiz", UUID.randomUUID(), "Café pergamino seco", Quantity.of("1"),
                ProductUnit.ARROBA, Money.ofCents(AMOUNT));
        Transaction transaction = Transaction.open(UUID.randomUUID(), purchase, CREATED);
        store.transactions.put(transaction.id(), transaction);
        return transaction.id();
    }

    private static PaymentResult result(PaymentOutcome outcome) {
        return new PaymentResult("w-" + UUID.randomUUID(), outcome, AMOUNT, "COP", PaymentMethod.CARD);
    }

    @Test
    void onlyPaymentsFiveMinutesPastTheirCheckoutAreChecked() {
        job.settleExpired();

        assertThat(store.askedCutoff).isEqualTo(NOW.minus(SettleExpiredPaymentsService.GRACE));
        assertThat(SettleExpiredPaymentsService.GRACE).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void anApprovedPaymentFoundByReferenceConfirms() {
        UUID id = pending();
        store.provider.put(id, result(PaymentOutcome.APPROVED));

        assertThat(job.settleExpired()).isEqualTo(1);
        assertThat(store.transactions.get(id).status()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(store.outbox).singleElement()
                .satisfies(event -> assertThat(event.type()).isEqualTo(TransactionEvent.Type.TRANSACTION_CONFIRMED));
    }

    @Test
    void aRejectedPaymentFailsAsPaymentFailed() {
        UUID id = pending();
        store.provider.put(id, result(PaymentOutcome.REJECTED));

        job.settleExpired();

        assertThat(store.transactions.get(id).status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(store.outbox).singleElement()
                .satisfies(event -> assertThat(event.reason()).isEqualTo(FailureReason.PAYMENT_FAILED));
    }

    @Test
    void withNoPaymentOrAStillPendingOneItFailsAsExpired() {
        UUID none = pending();
        UUID stillPending = pending();
        store.provider.put(stillPending, result(PaymentOutcome.PENDING));

        assertThat(job.settleExpired()).isEqualTo(2);
        assertThat(store.transactions.get(none).status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(store.transactions.get(stillPending).status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(store.outbox).hasSize(2).allSatisfy(event -> {
            assertThat(event.type()).isEqualTo(TransactionEvent.Type.TRANSACTION_FAILED);
            assertThat(event.reason()).isEqualTo(FailureReason.EXPIRED);
        });
    }

    @Test
    void withNoAnswerItStaysPendingAndTheOthersGoOn() {
        UUID unanswered = pending();
        UUID none = pending();
        store.unanswered = Set.of(unanswered);

        assertThat(job.settleExpired()).isEqualTo(1);
        assertThat(store.transactions.get(unanswered).status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(store.transactions.get(none).status()).isEqualTo(TransactionStatus.FAILED);
    }
}

package co.edu.corhuila.marketagri.transactions.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.transactions.application.port.in.CreatePayment.Command;
import co.edu.corhuila.marketagri.transactions.application.port.in.CreatePayment.Payment;
import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentGateway;
import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionCreations;
import co.edu.corhuila.marketagri.transactions.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.transactions.domain.model.DomainException;
import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentGatewayException;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionInProgressException;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionStatus;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** I-06 in the core (D-C35): one transaction, the replay rolls back, and rows 2–4 of the card. */
class CreatePaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T15:00:00Z");
    private static final UUID SAGA = UUID.fromString("5a6a0000-0000-4000-8000-000000000001");
    private static final UUID TX = UUID.fromString("7a1f0c2e-0000-4000-8000-000000000001");
    private static final UUID CONVERSATION = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    /** Commits what the work wrote, or throws it away when the work throws (like a rollback). */
    private static final class FakeStore implements UnitOfWork, TransactionCreations {
        final Map<UUID, Transaction> transactions = new LinkedHashMap<>();
        final Map<String, UUID> keys = new HashMap<>();
        private Map<UUID, Transaction> pendingTransactions;
        private Map<String, UUID> pendingKeys;

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            pendingTransactions = new LinkedHashMap<>(transactions);
            pendingKeys = new HashMap<>(keys);
            try {
                T result = work.get();
                transactions.clear();
                transactions.putAll(pendingTransactions);
                keys.clear();
                keys.putAll(pendingKeys);
                return result;
            } finally {
                pendingTransactions = null;
                pendingKeys = null;
            }
        }

        /** Like ON CONFLICT (id) DO NOTHING: the id is checked first, then the partial unique index. */
        @Override
        public boolean add(Transaction transaction) {
            assertThat(pendingTransactions).as("add inside the unit of work").isNotNull();
            if (pendingTransactions.containsKey(transaction.id())) {
                return false;
            }
            UUID conversation = transaction.purchase().conversationId();
            boolean inProgress = pendingTransactions.values().stream().anyMatch(stored ->
                    stored.status() == TransactionStatus.PENDING
                            && stored.purchase().conversationId().equals(conversation));
            if (transaction.status() == TransactionStatus.PENDING && inProgress) {
                throw new TransactionInProgressException(conversation);
            }
            pendingTransactions.put(transaction.id(), transaction);
            return true;
        }

        @Override
        public boolean claimKey(UUID callerId, String idempotencyKey, UUID transactionId) {
            assertThat(pendingKeys).as("claim inside the unit of work").isNotNull();
            return pendingKeys.putIfAbsent(callerId + "/" + idempotencyKey, transactionId) == null;
        }

        @Override
        public Optional<Transaction> transactionForKey(UUID callerId, String idempotencyKey) {
            return Optional.ofNullable(keys.get(callerId + "/" + idempotencyKey)).map(transactions::get);
        }
    }

    /** A link that depends only on the transaction, as the real adapters build it. */
    private static final class FakeGateway implements PaymentGateway {
        boolean broken;

        @Override
        public URI checkoutUrl(Transaction transaction) {
            if (broken) {
                throw new PaymentGatewayException("WOMPI_PUBLIC_KEY is missing");
            }
            return URI.create("https://checkout.test/p/?reference=" + transaction.id()
                    + "&amount-in-cents=" + transaction.amount().cents()
                    + "&expiration-time=" + transaction.expiresAt());
        }
    }

    private final FakeStore store = new FakeStore();
    private final FakeGateway gateway = new FakeGateway();
    private final CreatePaymentService service = new CreatePaymentService(store, gateway, store,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static Command command(UUID transactionId, String key, UUID conversation, String quantity) {
        return new Command(SAGA, key, transactionId, conversation,
                UUID.fromString("b0000000-0000-4000-8000-000000000001"), "Carlos Pérez",
                UUID.fromString("a1b2c3d4-4444-0000-0000-000000000001"), "Ana Ruiz",
                UUID.fromString("d0000000-0000-4000-8000-000000000001"), "Café pergamino seco",
                new BigDecimal(quantity), ProductUnit.ARROBA, 8_500_000);
    }

    private static Command command(String quantity) {
        return command(TX, TX.toString(), CONVERSATION, quantity);
    }

    private static void assertCode(Throwable thrown, String code) {
        assertThat(((DomainException) thrown).code()).isEqualTo(code);
    }

    @Test
    void creatingStoresAPendingPaymentWithItsKeyAndAnswersTheCheckoutLink() {
        Payment payment = service.create(command("2.5"));

        Transaction transaction = payment.transaction();
        assertThat(payment.created()).isTrue();
        assertThat(transaction.id()).isEqualTo(TX);
        assertThat(transaction.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transaction.amount()).isEqualTo(Money.ofCents(21_250_000));
        assertThat(payment.checkoutUrl()).isEqualTo(gateway.checkoutUrl(transaction));
        assertThat(store.transactions).containsKey(TX);
        assertThat(store.keys).containsEntry(SAGA + "/" + TX, TX);
    }

    @Test
    void aReplayWithTheSameBodyAnswersTheOriginalAndTheSameLink() {
        Payment original = service.create(command("2.5"));

        Payment replay = service.create(command("2.50"));

        assertThat(replay.created()).isFalse();
        assertThat(replay.transaction()).isSameAs(original.transaction());
        assertThat(replay.checkoutUrl()).isEqualTo(original.checkoutUrl());
        assertThat(store.transactions).hasSize(1);
    }

    @Test
    void theSameKeyWithOtherDataIsAMismatchAndChangesNothing() {
        service.create(command("2.5"));

        assertThatThrownBy(() -> service.create(command("3")))
                .isInstanceOfSatisfying(DomainException.class, refused -> assertCode(refused, "TRANSACTION_MISMATCH"));
        assertThat(store.transactions.get(TX).amount()).isEqualTo(Money.ofCents(21_250_000));
    }

    @Test
    void theSameIdUnderAnotherKeyIsAMismatch() {
        service.create(command("2.5"));

        assertThatThrownBy(() -> service.create(command(TX, "another-key", CONVERSATION, "2.5")))
                .isInstanceOfSatisfying(DomainException.class, refused -> assertCode(refused, "TRANSACTION_MISMATCH"));
        assertThat(store.keys).hasSize(1);
    }

    @Test
    void aSecondPendingPaymentForTheConversationIsInProgressAndStoresNothing() {
        service.create(command("2.5"));
        UUID other = UUID.fromString("7a1f0c2e-0000-4000-8000-000000000002");

        assertThatThrownBy(() -> service.create(command(other, other.toString(), CONVERSATION, "1")))
                .isInstanceOfSatisfying(DomainException.class,
                        refused -> assertCode(refused, "TRANSACTION_IN_PROGRESS"));
        assertThat(store.transactions).hasSize(1);
        assertThat(store.keys).hasSize(1);
    }

    @Test
    void whenTheLinkCannotBeBuiltThePaymentIsStoredFailedWithItsKey() {
        gateway.broken = true;

        assertThatThrownBy(() -> service.create(command("2.5")))
                .isInstanceOfSatisfying(DomainException.class, refused -> assertCode(refused, "PAYMENT_GATEWAY_ERROR"));
        Transaction stored = store.transactions.get(TX);
        assertThat(stored.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(stored.failedAt()).isEqualTo(NOW);
        assertThat(store.keys).containsEntry(SAGA + "/" + TX, TX);
    }

    @Test
    void aReplayOfAPaymentThatNeverOpenedGetsTheSameErrorEvenIfTheGatewayIsBack() {
        gateway.broken = true;
        assertThatThrownBy(() -> service.create(command("2.5"))).isInstanceOf(PaymentGatewayException.class);
        gateway.broken = false;

        assertThatThrownBy(() -> service.create(command("2.5")))
                .isInstanceOfSatisfying(DomainException.class, refused -> assertCode(refused, "PAYMENT_GATEWAY_ERROR"));
        assertThat(store.transactions).hasSize(1);
    }

    @Test
    void aFailedPaymentDoesNotBlockANewOneForTheConversation() {
        gateway.broken = true;
        assertThatThrownBy(() -> service.create(command("2.5"))).isInstanceOf(PaymentGatewayException.class);
        gateway.broken = false;
        UUID retry = UUID.fromString("7a1f0c2e-0000-4000-8000-000000000003");

        assertThat(service.create(command(retry, retry.toString(), CONVERSATION, "2.5")).created()).isTrue();
        assertThat(store.transactions).hasSize(2);
    }

    @Test
    void anInvalidQuantityIsRefusedBeforeAnythingIsStored() {
        assertThatThrownBy(() -> service.create(command("0")))
                .isInstanceOfSatisfying(DomainException.class, refused -> assertCode(refused, "VALIDATION_ERROR"));
        assertThat(store.transactions).isEmpty();
    }
}

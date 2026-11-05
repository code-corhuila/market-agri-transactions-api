package co.edu.corhuila.marketagri.transactions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The payment aggregate: born PENDING with its checkout window, or FAILED at once (I-06, ADR-019, AGGR-INV-005). */
class TransactionTest {

    static final UUID ID = UUID.fromString("7a1f0c2e-0000-4000-8000-000000000001");
    static final Instant NOW = Instant.parse("2026-11-04T15:00:00.123456789Z");
    static final Instant CREATED = Instant.parse("2026-11-04T15:00:00Z");

    static PurchaseSnapshot purchase(String quantity) {
        return new PurchaseSnapshot(UUID.fromString("c0000000-0000-4000-8000-000000000001"),
                UUID.fromString("b0000000-0000-4000-8000-000000000001"), "Carlos Pérez",
                UUID.fromString("a1b2c3d4-4444-0000-0000-000000000001"), "Ana Ruiz",
                UUID.fromString("d0000000-0000-4000-8000-000000000001"), "Café pergamino seco",
                Quantity.of(quantity), ProductUnit.ARROBA, Money.ofCents(8_500_000));
    }

    @Test
    void openingCreatesAPendingPaymentWithTheComputedAmount() {
        Transaction transaction = Transaction.open(ID, purchase("2.5"), NOW);

        assertThat(transaction.id()).isEqualTo(ID);
        assertThat(transaction.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transaction.amount()).isEqualTo(Money.ofCents(21_250_000));
        assertThat(transaction.paymentMethod()).isNull();
        assertThat(transaction.gatewayTransactionId()).isNull();
        assertThat(transaction.confirmedAt()).isNull();
        assertThat(transaction.failedAt()).isNull();
    }

    @Test
    void theCheckoutExpiresThirtyMinutesAfterCreationInWholeSeconds() {
        Transaction transaction = Transaction.open(ID, purchase("1"), NOW);

        assertThat(transaction.createdAt()).isEqualTo(CREATED);
        assertThat(transaction.expiresAt()).isEqualTo(Instant.parse("2026-11-04T15:30:00Z"));
    }

    @Test
    void aPaymentThatCannotOpenFailsAtTheInstantItIsCreated() {
        Transaction failed = Transaction.open(ID, purchase("1"), NOW).failedToOpen();

        assertThat(failed.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(failed.failedAt()).isEqualTo(CREATED);
        assertThat(failed.neverOpened()).isTrue();
    }

    @Test
    void onlyAJustOpenedPaymentCanFailToOpen() {
        Transaction failed = Transaction.open(ID, purchase("1"), NOW).failedToOpen();

        assertThatThrownBy(failed::failedToOpen).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aPaymentThatFailedLaterWasOpened() {
        Transaction expired = new Transaction(ID, purchase("1"), Money.ofCents(8_500_000),
                TransactionStatus.FAILED, null, null, CREATED, CREATED.plus(Transaction.CHECKOUT_WINDOW), null,
                Instant.parse("2026-11-04T15:35:00Z"));

        assertThat(expired.neverOpened()).isFalse();
        assertThat(Transaction.open(ID, purchase("1"), NOW).neverOpened()).isFalse();
    }

    @Test
    void itsBuyerAndItsProducerMayReadItAndNobodyElse() {
        Transaction transaction = Transaction.open(ID, purchase("1"), NOW);

        transaction.ensureParticipant(transaction.purchase().buyerId());
        transaction.ensureParticipant(transaction.purchase().producerId());
        assertThatThrownBy(() -> transaction.ensureParticipant(UUID.randomUUID()))
                .isInstanceOfSatisfying(DomainException.class,
                        refused -> assertThat(refused.code()).isEqualTo("NOT_A_PARTICIPANT"));
    }

    @Test
    void rebuildingRefusesTimestampsThatDoNotMatchTheStatus() {
        assertThatThrownBy(() -> new Transaction(ID, purchase("1"), Money.ofCents(8_500_000),
                TransactionStatus.CONFIRMED, PaymentMethod.CARD, "wompi-1", CREATED,
                CREATED.plus(Transaction.CHECKOUT_WINDOW), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rebuildingRefusesAnAmountThatIsNotTheComputedOne() {
        assertThatThrownBy(() -> new Transaction(ID, purchase("2"), Money.ofCents(8_500_000),
                TransactionStatus.PENDING, null, null, CREATED, CREATED.plus(Transaction.CHECKOUT_WINDOW), null,
                null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rebuildingRefusesAPendingPaymentWithAMethodOrAConfirmationWithoutTheProviderPayment() {
        assertThatThrownBy(() -> new Transaction(ID, purchase("1"), Money.ofCents(8_500_000),
                TransactionStatus.PENDING, PaymentMethod.NEQUI, null, CREATED,
                CREATED.plus(Transaction.CHECKOUT_WINDOW), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Transaction(ID, purchase("1"), Money.ofCents(8_500_000),
                TransactionStatus.CONFIRMED, PaymentMethod.CARD, null, CREATED,
                CREATED.plus(Transaction.CHECKOUT_WINDOW), CREATED.plusSeconds(60), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package co.edu.corhuila.marketagri.transactions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The events of api-contract §6, as the outbox keeps them (ADR-014): one per settled payment. */
class TransactionEventTest {

    private static final UUID EVENT = UUID.fromString("e0000000-0000-4000-8000-000000000001");
    private static final Instant LATER = Instant.parse("2026-11-04T15:07:30Z");
    private static final PaymentResult APPROVED =
            new PaymentResult("w-1", PaymentOutcome.APPROVED, 8_500_000, "COP", PaymentMethod.CARD);
    private static final PaymentResult REJECTED =
            new PaymentResult("w-1", PaymentOutcome.REJECTED, 8_500_000, "COP", PaymentMethod.CARD);

    private static Transaction pending() {
        return Transaction.open(TransactionTest.ID, TransactionTest.purchase("1"), TransactionTest.NOW);
    }

    @Test
    void aConfirmedPaymentGivesTransactionConfirmedAtItsConfirmation() {
        TransactionEvent event = TransactionEvent.confirmed(EVENT, pending().confirm(APPROVED, LATER), "corr-1", "w-1");

        assertThat(event.type()).isEqualTo(TransactionEvent.Type.TRANSACTION_CONFIRMED);
        assertThat(event.type().eventType()).isEqualTo("TransactionConfirmed");
        assertThat(event.eventId()).isEqualTo(EVENT);
        assertThat(event.occurredAt()).isEqualTo(LATER);
        assertThat(event.reason()).isNull();
        assertThat(event.correlationId()).isEqualTo("corr-1");
        assertThat(event.causationId()).isEqualTo("w-1");
    }

    @Test
    void aFailedPaymentGivesTransactionFailedWithItsReason() {
        TransactionEvent event = TransactionEvent.failed(EVENT, pending().fail(REJECTED, LATER),
                FailureReason.PAYMENT_FAILED, "corr-1", "w-1");

        assertThat(event.type().eventType()).isEqualTo("TransactionFailed");
        assertThat(event.reason()).isEqualTo(FailureReason.PAYMENT_FAILED);
        assertThat(event.occurredAt()).isEqualTo(LATER);
    }

    @Test
    void anEventMatchesTheStateOfItsPayment() {
        assertThatThrownBy(() -> TransactionEvent.confirmed(EVENT, pending(), "corr-1", "w-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TransactionEvent.failed(EVENT, pending().confirm(APPROVED, LATER),
                FailureReason.PAYMENT_FAILED, "corr-1", "w-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TransactionEvent.failed(EVENT, pending().fail(REJECTED, LATER), null, "corr-1", "w-1"))
                .isInstanceOf(NullPointerException.class);
    }
}

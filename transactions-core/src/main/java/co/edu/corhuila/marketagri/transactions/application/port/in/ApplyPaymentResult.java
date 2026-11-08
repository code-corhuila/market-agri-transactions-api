package co.edu.corhuila.marketagri.transactions.application.port.in;

import co.edu.corhuila.marketagri.transactions.domain.model.PaymentResult;
import java.util.UUID;

/**
 * Applies a verified result of the provider to its payment (E-43; the expiry job too, ADR-019).
 * Every case answers an {@link Outcome}: the webhook answers 200 for all of them and logs it.
 */
public interface ApplyPaymentResult {

    Outcome apply(Command command);

    /** {@code transactionId} is the provider's {@code reference}; {@code correlationId} goes into the event. */
    record Command(UUID transactionId, PaymentResult result, String correlationId) {
    }

    enum Outcome {
        /** PENDING → CONFIRMED, with {@code TransactionConfirmed} in the outbox. */
        CONFIRMED,
        /** PENDING → FAILED, with {@code TransactionFailed} ({@code PAYMENT_FAILED}) in the outbox. */
        FAILED,
        /** The provider has no final result yet: nothing changes. */
        STILL_PENDING,
        /** No payment with this reference: nothing changes. */
        UNKNOWN_TRANSACTION,
        /** Already CONFIRMED or FAILED (AGGR-INV-005), e.g. a redelivered event: nothing changes. */
        ALREADY_SETTLED,
        /** Another amount or currency, or a provider payment id of another payment: a security warning. */
        REFUSED_BY_GUARD
    }
}

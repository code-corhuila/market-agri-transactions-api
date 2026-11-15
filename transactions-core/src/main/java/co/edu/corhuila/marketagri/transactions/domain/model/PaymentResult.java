package co.edu.corhuila.marketagri.transactions.domain.model;

import java.util.Objects;

/**
 * A result of the provider whose authenticity was already verified (the webhook checksum or the
 * query with the private key, ADR-019). {@code paymentMethod} may be unknown on a failure.
 */
public record PaymentResult(String gatewayTransactionId, PaymentOutcome outcome, long amountCents,
                            String currency, PaymentMethod paymentMethod) {

    /** The {@code gateway_transaction_id} column keeps 1–64 characters. */
    private static final int MAX_ID_LENGTH = 64;

    public PaymentResult {
        Objects.requireNonNull(outcome, "outcome");
        if (gatewayTransactionId == null || gatewayTransactionId.isBlank()
                || gatewayTransactionId.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("the provider payment id must have 1–" + MAX_ID_LENGTH + " characters");
        }
    }
}

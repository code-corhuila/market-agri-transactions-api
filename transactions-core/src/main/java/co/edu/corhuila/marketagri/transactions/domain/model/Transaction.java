package co.edu.corhuila.marketagri.transactions.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * One on-platform payment (api-contract §4.4, `TransactionView`). Immutable: a change of state is a
 * new value. The constructor refuses what the rules do not allow, so a stored row is rebuilt with it.
 */
public record Transaction(UUID id, PurchaseSnapshot purchase, Money amount, TransactionStatus status,
                          PaymentMethod paymentMethod, String gatewayTransactionId, Instant createdAt,
                          Instant expiresAt, Instant confirmedAt, Instant failedAt) {

    /** ADR-019: the Web Checkout link expires 30 minutes after the payment is created. */
    public static final Duration CHECKOUT_WINDOW = Duration.ofMinutes(30);

    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        if (!amount.equals(purchase.amount())) {
            throw new IllegalArgumentException("amount is not round_half_up(quantity × unitPrice): " + id);
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("checkout expires before it is created: " + id);
        }
        boolean timestampsMatch = switch (status) {
            case PENDING -> confirmedAt == null && failedAt == null && paymentMethod == null;
            case CONFIRMED -> confirmedAt != null && failedAt == null && gatewayTransactionId != null;
            case FAILED -> failedAt != null && confirmedAt == null;
        };
        if (!timestampsMatch) {
            throw new IllegalArgumentException(status + " breaks the rules of its state (AGGR-INV-005): " + id);
        }
    }

    /**
     * A new {@code PENDING} payment with the id the saga reserved with (I-03). Times are cut to
     * whole seconds: the checkout link signs {@code expiresAt}, and the same value must come back
     * from the database (which keeps microseconds) for a replay to rebuild the same link.
     */
    public static Transaction open(UUID id, PurchaseSnapshot purchase, Instant now) {
        Instant createdAt = now.truncatedTo(ChronoUnit.SECONDS);
        return new Transaction(id, purchase, purchase.amount(), TransactionStatus.PENDING, null, null,
                createdAt, createdAt.plus(CHECKOUT_WINDOW), null, null);
    }

    /** I-06 row 4: the checkout could not be built, so the payment fails when it is created, with no event. */
    public Transaction failedToOpen() {
        if (status != TransactionStatus.PENDING || gatewayTransactionId != null) {
            throw new IllegalStateException("only a payment that was just opened can fail to open: " + id);
        }
        return new Transaction(id, purchase, amount, TransactionStatus.FAILED, null, null, createdAt, expiresAt,
                null, createdAt);
    }

    /** True for a payment that never reached the provider; a replay of it gets the same error. */
    public boolean neverOpened() {
        return status == TransactionStatus.FAILED && createdAt.equals(failedAt) && gatewayTransactionId == null;
    }
}

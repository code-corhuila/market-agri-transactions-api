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

    /** The currency of every payment (ADR-019); the provider must answer in it too. */
    public static final String CURRENCY = "COP";

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

    /** CONFIRMED and FAILED are terminal (AGGR-INV-005). */
    public boolean isPending() {
        return status == TransactionStatus.PENDING;
    }

    /**
     * An approved result of the provider confirms the payment (E-43, AGGR-INV-004), after the
     * guards of the contract: the same amount and currency. The instant is cut to whole seconds.
     */
    public Transaction confirm(PaymentResult result, Instant now) {
        requireSettleable(result, PaymentOutcome.APPROVED);
        if (result.amountCents() != amount.cents()) {
            throw new PaymentResultMismatchException("amount " + result.amountCents() + " is not " + amount.cents()
                    + " for " + id);
        }
        if (!CURRENCY.equals(result.currency())) {
            throw new PaymentResultMismatchException("currency " + result.currency() + " is not " + CURRENCY
                    + " for " + id);
        }
        return new Transaction(id, purchase, amount, TransactionStatus.CONFIRMED, result.paymentMethod(),
                result.gatewayTransactionId(), createdAt, expiresAt, now.truncatedTo(ChronoUnit.SECONDS), null);
    }

    /**
     * The entry owed to the producer, only for a CONFIRMED payment (AGGR-INV-006): the whole amount,
     * no commission (AGGR-INV-006b), dated at the confirmation.
     */
    public LedgerEntry ledgerEntry() {
        if (status != TransactionStatus.CONFIRMED) {
            throw new IllegalStateException("no ledger entry for a " + status + " transaction (AGGR-INV-006): " + id);
        }
        return new LedgerEntry(id, purchase.producerId(), amount, LedgerEntry.NO_FEE,
                LedgerStatus.PENDING_DISBURSEMENT, confirmedAt);
    }

    /** A rejected result of the provider (declined, voided or an error) fails the payment (E-43). */
    public Transaction fail(PaymentResult result, Instant now) {
        requireSettleable(result, PaymentOutcome.REJECTED);
        return new Transaction(id, purchase, amount, TransactionStatus.FAILED, result.paymentMethod(),
                result.gatewayTransactionId(), createdAt, expiresAt, null, now.truncatedTo(ChronoUnit.SECONDS));
    }

    /** The checkout ended and the provider has no final result (expiry job, FR-025): FAILED, no provider payment. */
    public Transaction expire(Instant now) {
        if (!isPending()) {
            throw new IllegalStateException(status + " is terminal (AGGR-INV-005): " + id);
        }
        return new Transaction(id, purchase, amount, TransactionStatus.FAILED, null, null, createdAt, expiresAt,
                null, now.truncatedTo(ChronoUnit.SECONDS));
    }

    private void requireSettleable(PaymentResult result, PaymentOutcome expected) {
        if (!isPending()) {
            throw new IllegalStateException(status + " is terminal (AGGR-INV-005): " + id);
        }
        if (result.outcome() != expected) {
            throw new IllegalArgumentException("a " + result.outcome() + " result cannot settle as " + expected);
        }
    }

    /** E-41: only its buyer or its producer may read it (the caller comes from the token, never from the request). */
    public void ensureParticipant(UUID userId) {
        if (!userId.equals(purchase.buyerId()) && !userId.equals(purchase.producerId())) {
            throw new NotAParticipantException(id);
        }
    }

    /** True for a payment that never reached the provider; a replay of it gets the same error. */
    public boolean neverOpened() {
        return status == TransactionStatus.FAILED && createdAt.equals(failedAt) && gatewayTransactionId == null;
    }
}

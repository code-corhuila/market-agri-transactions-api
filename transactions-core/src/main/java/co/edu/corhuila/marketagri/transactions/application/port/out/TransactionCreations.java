package co.edu.corhuila.marketagri.transactions.application.port.out;

import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionInProgressException;
import java.util.Optional;
import java.util.UUID;

/** Where a new payment and its {@code Idempotency-Key} are stored, in one {@link UnitOfWork} (D-C35). */
public interface TransactionCreations {

    /**
     * False when a payment with this id already exists: nothing is written.
     *
     * @throws TransactionInProgressException when its conversation already has a {@code PENDING} payment
     */
    boolean add(Transaction transaction);

    /** False when the caller already used the key: nothing is written. */
    boolean claimKey(UUID callerId, String idempotencyKey, UUID transactionId);

    /** The payment created earlier with this key, if any. */
    Optional<Transaction> transactionForKey(UUID callerId, String idempotencyKey);
}

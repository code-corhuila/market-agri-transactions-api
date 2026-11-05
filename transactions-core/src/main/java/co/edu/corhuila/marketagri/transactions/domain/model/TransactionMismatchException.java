package co.edu.corhuila.marketagri.transactions.domain.model;

import java.util.UUID;

/** I-06 row 2: the payment {@code transactionId} (or its key) already exists with other data. */
public class TransactionMismatchException extends DomainException {

    public TransactionMismatchException(UUID transactionId) {
        super("TRANSACTION_MISMATCH", "transaction already exists with other data: " + transactionId);
    }
}

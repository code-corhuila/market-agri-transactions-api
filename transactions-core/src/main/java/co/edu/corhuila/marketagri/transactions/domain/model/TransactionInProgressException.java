package co.edu.corhuila.marketagri.transactions.domain.model;

import java.util.UUID;

/** I-06 row 3: the conversation already has a {@code PENDING} payment (partial unique index of D-21). */
public class TransactionInProgressException extends DomainException {

    public TransactionInProgressException(UUID conversationId) {
        super("TRANSACTION_IN_PROGRESS", "a payment is already in progress for conversation " + conversationId);
    }
}

package co.edu.corhuila.marketagri.transactions.domain.model;

import java.util.UUID;

/** E-41 row 1: no payment with this id. */
public class TransactionNotFoundException extends DomainException {

    public TransactionNotFoundException(UUID id) {
        super("TRANSACTION_NOT_FOUND", "transaction not found: " + id);
    }
}

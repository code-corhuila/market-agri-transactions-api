package co.edu.corhuila.marketagri.transactions.application.port.out;

import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionEvent;
import java.util.Optional;
import java.util.UUID;

/** Where a payment leaves PENDING, always inside one {@link UnitOfWork} (E-43, ADR-014). */
public interface PaymentSettlements {

    /** The payment, locked until the unit of work ends, so two results never settle it twice. */
    Optional<Transaction> lockById(UUID id);

    /** True when another payment already carries this provider payment id (E-43 guard). */
    boolean gatewayTransactionIdTakenByAnother(String gatewayTransactionId, UUID transactionId);

    /** Stores the new state and its event for the relay, in the same transaction. */
    void settle(Transaction settled, TransactionEvent event);
}

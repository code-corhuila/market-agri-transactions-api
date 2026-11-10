package co.edu.corhuila.marketagri.transactions.application.port.in;

import co.edu.corhuila.marketagri.transactions.domain.model.Page;
import co.edu.corhuila.marketagri.transactions.domain.model.PageRequest;
import co.edu.corhuila.marketagri.transactions.domain.model.AdminTransactionFilter;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionFilter;
import java.util.UUID;

/** A buyer or a producer reads their payments (E-41, E-42, HU-TXN-001). */
public interface ReadTransactions {

    /** E-41: {@code TRANSACTION_NOT_FOUND} first, then {@code NOT_A_PARTICIPANT}. */
    Transaction get(UUID id, UUID callerId);

    /** E-42: {@code createdAt} descending, then {@code id}. */
    Page<Transaction> mine(TransactionFilter filter, PageRequest request);

    /** E-45: authorization belongs to the HTTP adapter, so an admin reads by id without a participant check. */
    Transaction adminGet(UUID id);

    /** E-44: administration may combine any optional filter. */
    Page<Transaction> admin(AdminTransactionFilter filter, PageRequest request);
}

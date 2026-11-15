package co.edu.corhuila.marketagri.transactions.application.port.out;

import co.edu.corhuila.marketagri.transactions.domain.model.Page;
import co.edu.corhuila.marketagri.transactions.domain.model.PageRequest;
import co.edu.corhuila.marketagri.transactions.domain.model.AdminTransactionFilter;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionFilter;
import java.util.Optional;
import java.util.UUID;

/** Reading stored payments. Writing has its own ports, one per use case. */
public interface TransactionRepository {

    Optional<Transaction> findById(UUID id);

    /** The payments that match, {@code createdAt} descending then {@code id}, with the total that match. */
    Page<Transaction> findPage(TransactionFilter filter, PageRequest request);

    /** E-44: applies all present administrative restrictions with the standard order. */
    Page<Transaction> findPage(AdminTransactionFilter filter, PageRequest request);
}

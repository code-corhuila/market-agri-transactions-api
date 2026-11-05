package co.edu.corhuila.marketagri.transactions.application.usecase;

import co.edu.corhuila.marketagri.transactions.application.port.in.ReadTransactions;
import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionRepository;
import co.edu.corhuila.marketagri.transactions.domain.model.Page;
import co.edu.corhuila.marketagri.transactions.domain.model.PageRequest;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionFilter;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionNotFoundException;
import java.util.UUID;

/** E-41 and E-42: the rule of who may read lives in the aggregate; this class only finds. */
public final class ReadTransactionsService implements ReadTransactions {

    private final TransactionRepository transactions;

    public ReadTransactionsService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    @Override
    public Transaction get(UUID id, UUID callerId) {
        Transaction transaction = transactions.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
        transaction.ensureParticipant(callerId);
        return transaction;
    }

    @Override
    public Page<Transaction> mine(TransactionFilter filter, PageRequest request) {
        return transactions.findPage(filter, request);
    }
}

package co.edu.corhuila.marketagri.transactions.app;

import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionCreations;
import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionRepository;
import co.edu.corhuila.marketagri.transactions.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.transactions.domain.model.Page;
import co.edu.corhuila.marketagri.transactions.domain.model.PageRequest;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionFilter;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionInProgressException;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionStatus;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The persistence ports in memory for the HTTP tests, with the rules of the real tables: the id
 * first (ON CONFLICT), then the partial index of D-21, a rollback when the work throws, and the
 * order of E-42 ({@code createdAt} descending, then {@code id}).
 */
final class InMemoryTransactions implements TransactionCreations, TransactionRepository, UnitOfWork {

    private static final Comparator<Transaction> E42_ORDER =
            Comparator.comparing(Transaction::createdAt).reversed().thenComparing(Transaction::id);

    private final Map<UUID, Transaction> transactions = new LinkedHashMap<>();
    private final Map<String, UUID> keys = new HashMap<>();

    synchronized void clear() {
        transactions.clear();
        keys.clear();
    }

    synchronized int size() {
        return transactions.size();
    }

    @Override
    public synchronized <T> T inTransaction(Supplier<T> work) {
        Map<UUID, Transaction> savedTransactions = new LinkedHashMap<>(transactions);
        Map<String, UUID> savedKeys = new HashMap<>(keys);
        try {
            return work.get();
        } catch (RuntimeException rollback) {
            transactions.clear();
            transactions.putAll(savedTransactions);
            keys.clear();
            keys.putAll(savedKeys);
            throw rollback;
        }
    }

    @Override
    public synchronized boolean add(Transaction transaction) {
        if (transactions.containsKey(transaction.id())) {
            return false;
        }
        UUID conversation = transaction.purchase().conversationId();
        boolean inProgress = transactions.values().stream().anyMatch(stored -> stored.status() == TransactionStatus.PENDING
                && stored.purchase().conversationId().equals(conversation));
        if (transaction.status() == TransactionStatus.PENDING && inProgress) {
            throw new TransactionInProgressException(conversation);
        }
        transactions.put(transaction.id(), transaction);
        return true;
    }

    @Override
    public synchronized boolean claimKey(UUID callerId, String idempotencyKey, UUID transactionId) {
        return keys.putIfAbsent(callerId + "/" + idempotencyKey, transactionId) == null;
    }

    @Override
    public synchronized Optional<Transaction> transactionForKey(UUID callerId, String idempotencyKey) {
        return Optional.ofNullable(keys.get(callerId + "/" + idempotencyKey)).map(transactions::get);
    }

    @Override
    public synchronized Optional<Transaction> findById(UUID id) {
        return Optional.ofNullable(transactions.get(id));
    }

    @Override
    public synchronized Page<Transaction> findPage(TransactionFilter filter, PageRequest request) {
        List<Transaction> matching = transactions.values().stream().filter(filter::matches).sorted(E42_ORDER).toList();
        return new Page<>(matching.stream().skip(request.offset()).limit(request.limit()).toList(), request,
                matching.size());
    }
}

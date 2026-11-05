package co.edu.corhuila.marketagri.transactions.adapter.out.persistence;

import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionRepository;
import co.edu.corhuila.marketagri.transactions.domain.model.Page;
import co.edu.corhuila.marketagri.transactions.domain.model.PageRequest;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionFilter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link TransactionRepository} on {@code transactions.payment_transaction} (B2), as
 * transactions_app. E-42 uses the indexes {@code idx_payment_transaction_buyer_id_created_at} and
 * {@code …_producer_id_created_at}; rows come back through the aggregate's constructor.
 */
public final class JdbcTransactionRepository implements TransactionRepository {

    private final JdbcClient jdbc;

    public JdbcTransactionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Transaction> findById(UUID id) {
        return jdbc.sql("SELECT * FROM transactions.payment_transaction WHERE id = :id")
                .param("id", id)
                .query(JdbcTransactionCreations::transaction)
                .optional();
    }

    /**
     * One fixed fragment for the side and one for the status, as {@link TransactionFilter#matches};
     * the values always travel as parameters. The page and the total use the same conditions.
     */
    @Override
    public Page<Transaction> findPage(TransactionFilter filter, PageRequest request) {
        StringBuilder where = new StringBuilder(switch (filter.side()) {
            case BUYER -> "buyer_id = :userId";
            case PRODUCER -> "producer_id = :userId";
        });
        Map<String, Object> params = new HashMap<>();
        params.put("userId", filter.userId());
        if (filter.status() != null) {
            where.append(" AND status = :status");
            params.put("status", filter.status().name());
        }
        List<Transaction> items = jdbc.sql("SELECT * FROM transactions.payment_transaction WHERE " + where
                        + " ORDER BY created_at DESC, id ASC LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", request.limit())
                .param("offset", request.offset())
                .query(JdbcTransactionCreations::transaction)
                .list();
        long total = jdbc.sql("SELECT count(*) FROM transactions.payment_transaction WHERE " + where)
                .params(params)
                .query(Long.class)
                .single();
        return new Page<>(items, request, total);
    }
}

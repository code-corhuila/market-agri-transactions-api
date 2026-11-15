package co.edu.corhuila.marketagri.transactions.adapter.out.persistence;

import co.edu.corhuila.marketagri.transactions.application.port.out.UnitOfWork;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link UnitOfWork} with Spring's {@link TransactionTemplate}: the {@code JdbcClient} calls inside
 * share one connection and one transaction; a runtime exception rolls it back and is rethrown.
 */
public final class SpringUnitOfWork implements UnitOfWork {

    private final TransactionTemplate transactions;

    public SpringUnitOfWork(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}

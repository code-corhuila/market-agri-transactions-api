package co.edu.corhuila.marketagri.transactions.application.port.out;

import java.util.function.Supplier;

/**
 * One database transaction around a use case: everything inside commits together, and any
 * exception rolls everything back. The core asks for it without knowing who provides it.
 */
public interface UnitOfWork {

    <T> T inTransaction(Supplier<T> work);
}

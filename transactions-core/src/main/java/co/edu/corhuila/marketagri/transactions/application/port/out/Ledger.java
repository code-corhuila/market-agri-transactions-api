package co.edu.corhuila.marketagri.transactions.application.port.out;

import co.edu.corhuila.marketagri.transactions.domain.model.LedgerEntry;

/** The producers' ledger (D-C14): written inside the confirmation's {@link UnitOfWork} (AGGR-INV-006). */
@FunctionalInterface
public interface Ledger {

    /** Exactly one entry per transaction: a second one for the same transaction is refused by the store. */
    void add(LedgerEntry entry);
}

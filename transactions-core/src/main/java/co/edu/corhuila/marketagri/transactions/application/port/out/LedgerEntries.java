package co.edu.corhuila.marketagri.transactions.application.port.out;

import co.edu.corhuila.marketagri.transactions.domain.model.LedgerEntry;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Read-only ledger entries for E-41 and E-42; the collection avoids one query per transaction. */
public interface LedgerEntries {

    /** Entries indexed by their originating transaction. Absent means that payment has no ledger entry. */
    Map<UUID, LedgerEntry> findByTransactionIds(Collection<UUID> transactionIds);
}

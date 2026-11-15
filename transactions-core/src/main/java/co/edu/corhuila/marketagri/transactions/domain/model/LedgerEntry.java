package co.edu.corhuila.marketagri.transactions.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * What the platform owes the producer for one confirmed payment (D-C14, AGGR-INV-006): the gross
 * amount, the platform's fee (between 0 and the gross, in centavos) and the net that results. It
 * exists only through {@link Transaction#ledgerEntry()}.
 */
public record LedgerEntry(UUID transactionId, UUID producerId, Money grossAmount, long platformFeeCents,
                          LedgerStatus status, Instant createdAt) {

    /** No commission in the MVP (AGGR-INV-006b). */
    public static final long NO_FEE = 0;

    public LedgerEntry {
        if (platformFeeCents < 0 || platformFeeCents > grossAmount.cents()) {
            throw new IllegalArgumentException("platform fee " + platformFeeCents + " outside 0.."
                    + grossAmount.cents() + " for " + transactionId);
        }
    }

    public long netAmountCents() {
        return grossAmount.cents() - platformFeeCents;
    }
}

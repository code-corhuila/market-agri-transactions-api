package co.edu.corhuila.marketagri.transactions.adapter.in.http;

import co.edu.corhuila.marketagri.transactions.domain.model.LedgerEntry;
import java.time.Instant;

/** The producer's read-only disbursement record (D-C14). */
public record LedgerView(long grossAmountCents, long platformFeeCents, long netAmountCents, String currency,
                         String status, Instant createdAt) {

    private static final String CURRENCY = "COP";

    static LedgerView of(LedgerEntry entry) {
        return new LedgerView(entry.grossAmount().cents(), entry.platformFeeCents(), entry.netAmountCents(), CURRENCY,
                entry.status().name(), entry.createdAt());
    }
}

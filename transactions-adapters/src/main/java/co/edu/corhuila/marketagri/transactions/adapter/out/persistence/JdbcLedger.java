package co.edu.corhuila.marketagri.transactions.adapter.out.persistence;

import co.edu.corhuila.marketagri.transactions.application.port.out.Ledger;
import co.edu.corhuila.marketagri.transactions.domain.model.LedgerEntry;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link Ledger} on {@code ledger_entry} (B2), as transactions_app. The unique index on
 * {@code transaction_id} is the last guard of "exactly once" (AGGR-INV-006): a second entry fails
 * the statement, and with it the whole confirmation.
 */
public final class JdbcLedger implements Ledger {

    private final JdbcClient jdbc;

    public JdbcLedger(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(LedgerEntry entry) {
        jdbc.sql("""
                        INSERT INTO transactions.ledger_entry (transaction_id, producer_id, gross_amount_cents,
                                                               platform_fee_cents, net_amount_cents, status, created_at)
                        VALUES (:transaction, :producer, :gross, :fee, :net, :status, :createdAt)""")
                .param("transaction", entry.transactionId())
                .param("producer", entry.producerId())
                .param("gross", entry.grossAmount().cents())
                .param("fee", entry.platformFeeCents())
                .param("net", entry.netAmountCents())
                .param("status", entry.status().name())
                .param("createdAt", entry.createdAt().atOffset(ZoneOffset.UTC))
                .update();
    }

}

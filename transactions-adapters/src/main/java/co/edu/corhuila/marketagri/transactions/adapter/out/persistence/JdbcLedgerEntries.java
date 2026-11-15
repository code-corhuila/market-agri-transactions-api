package co.edu.corhuila.marketagri.transactions.adapter.out.persistence;

import co.edu.corhuila.marketagri.transactions.application.port.out.LedgerEntries;
import co.edu.corhuila.marketagri.transactions.domain.model.LedgerEntry;
import co.edu.corhuila.marketagri.transactions.domain.model.LedgerStatus;
import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Batch reads of the producer ledger for E-41 and E-42. */
public final class JdbcLedgerEntries implements LedgerEntries {
    private final JdbcClient jdbc;
    public JdbcLedgerEntries(JdbcClient jdbc) { this.jdbc = jdbc; }
    @Override public Map<UUID, LedgerEntry> findByTransactionIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return jdbc.sql("SELECT transaction_id, producer_id, gross_amount_cents, platform_fee_cents, status, created_at FROM transactions.ledger_entry WHERE transaction_id IN (:ids)")
                .param("ids", ids).query((row, ignored) -> new LedgerEntry(row.getObject("transaction_id", UUID.class), row.getObject("producer_id", UUID.class), Money.ofCents(row.getLong("gross_amount_cents")), row.getLong("platform_fee_cents"), LedgerStatus.valueOf(row.getString("status")), row.getObject("created_at", OffsetDateTime.class).toInstant()))
                .stream().collect(Collectors.toMap(LedgerEntry::transactionId, Function.identity()));
    }
}

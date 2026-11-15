package co.edu.corhuila.marketagri.transactions.adapter.out.persistence;

import co.edu.corhuila.marketagri.transactions.application.port.out.ExpiredPayments;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** {@link ExpiredPayments} on the partial index {@code idx_payment_transaction_pending_expires_at} (B2). */
public final class JdbcExpiredPayments implements ExpiredPayments {

    private final JdbcClient jdbc;

    public JdbcExpiredPayments(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> pendingExpiredBefore(Instant cutoff, int limit) {
        return jdbc.sql("""
                        SELECT id FROM transactions.payment_transaction
                         WHERE status = 'PENDING' AND expires_at < :cutoff ORDER BY expires_at LIMIT :limit""")
                .param("cutoff", cutoff.atOffset(ZoneOffset.UTC))
                .param("limit", limit)
                .query(UUID.class)
                .list();
    }
}

package co.edu.corhuila.marketagri.transactions.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** PENDING payments whose checkout ended before a cutoff (the partial index on {@code expires_at}). */
public interface ExpiredPayments {

    List<UUID> pendingExpiredBefore(Instant cutoff, int limit);
}

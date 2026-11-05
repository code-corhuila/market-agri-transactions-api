package co.edu.corhuila.marketagri.transactions.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Which payments E-42 lists: the caller's purchases or sales, and optionally one status. The
 * in-memory repository uses {@link #matches} and the SQL adapter is tested against it, so both
 * mean the same list (as {@code ProductFilter} in catalog).
 */
public record TransactionFilter(Participant side, UUID userId, TransactionStatus status) {

    public TransactionFilter {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(userId, "userId");
    }

    public boolean matches(Transaction transaction) {
        PurchaseSnapshot purchase = transaction.purchase();
        UUID participant = side == Participant.BUYER ? purchase.buyerId() : purchase.producerId();
        return participant.equals(userId) && (status == null || status == transaction.status());
    }
}

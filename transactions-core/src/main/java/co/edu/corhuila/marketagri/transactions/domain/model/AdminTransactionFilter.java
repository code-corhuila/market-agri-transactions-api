package co.edu.corhuila.marketagri.transactions.domain.model;

import java.util.UUID;

/** Optional E-44 restrictions, combined with AND and without a caller-owned side. */
public record AdminTransactionFilter(TransactionStatus status, UUID producerId, UUID buyerId) {

    public boolean matches(Transaction transaction) {
        PurchaseSnapshot purchase = transaction.purchase();
        return (status == null || status == transaction.status())
                && (producerId == null || producerId.equals(purchase.producerId()))
                && (buyerId == null || buyerId.equals(purchase.buyerId()));
    }
}

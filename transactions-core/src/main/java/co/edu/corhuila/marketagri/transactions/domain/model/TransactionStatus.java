package co.edu.corhuila.marketagri.transactions.domain.model;

/** api-contract `TransactionStatus`. {@code CONFIRMED} and {@code FAILED} are terminal (AGGR-INV-005). */
public enum TransactionStatus {
    PENDING, CONFIRMED, FAILED
}

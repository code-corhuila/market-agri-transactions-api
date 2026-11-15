package co.edu.corhuila.marketagri.transactions.domain.model;

/** Why a payment failed, as {@code TransactionFailed.reason} carries it (api-contract §6). */
public enum FailureReason {
    EXPIRED, PAYMENT_FAILED
}

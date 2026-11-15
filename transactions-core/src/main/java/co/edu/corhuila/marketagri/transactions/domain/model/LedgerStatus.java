package co.edu.corhuila.marketagri.transactions.domain.model;

/** Whether the platform already paid the producer (D-C14); every MVP flow ends {@code PENDING_DISBURSEMENT}. */
public enum LedgerStatus {
    PENDING_DISBURSEMENT,
    DISBURSED
}

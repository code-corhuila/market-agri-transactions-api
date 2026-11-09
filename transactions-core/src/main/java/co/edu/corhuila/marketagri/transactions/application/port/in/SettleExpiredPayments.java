package co.edu.corhuila.marketagri.transactions.application.port.in;

/** One run of the expiry job (E-43, FR-025 amended 2026-11-04). */
public interface SettleExpiredPayments {

    /** How many expired payments left PENDING in this run. */
    int settleExpired();
}

package co.edu.corhuila.marketagri.transactions.domain.model;

/** How the buyer paid, from the provider's result (api-contract `PaymentMethod`, ADR-019). */
public enum PaymentMethod {
    CARD, NEQUI, PSE, BANCOLOMBIA_TRANSFER, OTHER
}

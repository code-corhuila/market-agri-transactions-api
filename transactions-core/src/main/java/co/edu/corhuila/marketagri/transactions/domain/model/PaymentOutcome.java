package co.edu.corhuila.marketagri.transactions.domain.model;

/**
 * What the provider says about a payment (E-43, ADR-019), in the terms of this domain: the adapter
 * of each provider maps its own statuses ({@code APPROVED}; {@code DECLINED}, {@code VOIDED},
 * {@code ERROR}; {@code PENDING} in Wompi).
 */
public enum PaymentOutcome {
    APPROVED, REJECTED, PENDING
}

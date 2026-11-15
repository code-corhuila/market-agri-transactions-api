package co.edu.corhuila.marketagri.transactions.application.port.out;

import co.edu.corhuila.marketagri.transactions.domain.model.PaymentLookupException;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentResult;
import java.util.Optional;
import java.util.UUID;

/** The provider's own record of a payment, asked by reference (ADR-019: the expiry job). */
public interface PaymentLookup {

    /** Empty when the provider has no payment for it; {@link PaymentLookupException} when it does not answer. */
    Optional<PaymentResult> findByReference(UUID transactionId);
}

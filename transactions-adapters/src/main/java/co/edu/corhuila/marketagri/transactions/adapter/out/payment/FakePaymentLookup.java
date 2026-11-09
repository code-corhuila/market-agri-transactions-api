package co.edu.corhuila.marketagri.transactions.adapter.out.payment;

import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentLookup;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentResult;
import java.util.Optional;
import java.util.UUID;

/** {@code PAYMENT_GATEWAY=fake}: the fake provider never has a payment, so an expired checkout fails EXPIRED (Annex A T7). */
public final class FakePaymentLookup implements PaymentLookup {

    @Override
    public Optional<PaymentResult> findByReference(UUID transactionId) {
        return Optional.empty();
    }
}

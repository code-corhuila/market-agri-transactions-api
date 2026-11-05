package co.edu.corhuila.marketagri.transactions.application.port.out;

import co.edu.corhuila.marketagri.transactions.domain.model.PaymentGatewayException;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import java.net.URI;

/**
 * The payment provider, behind a port (Strategy, ADR-019): {@code wompi} (Web Checkout) or
 * {@code fake} (CI, integration and E2E), chosen by {@code PAYMENT_GATEWAY}.
 */
public interface PaymentGateway {

    /**
     * Where the buyer pays: a pure computation of the transaction, so a replay rebuilds the same
     * link instead of storing it. {@link PaymentGatewayException} when it cannot be built.
     */
    URI checkoutUrl(Transaction transaction);
}

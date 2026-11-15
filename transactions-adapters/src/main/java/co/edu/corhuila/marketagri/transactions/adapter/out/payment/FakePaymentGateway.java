package co.edu.corhuila.marketagri.transactions.adapter.out.payment;

import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentGateway;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import java.net.URI;

/**
 * {@code PAYMENT_GATEWAY=fake} (ADR-019): CI, integration and the automated E2E suite. The link is
 * the interface's page of the transaction itself, as the simulated mode of the front already does,
 * so the buyer "pays" without leaving the site; the signed test event arrives with E-43 (D4a).
 */
public final class FakePaymentGateway implements PaymentGateway {

    private final RedirectUrls redirects;

    public FakePaymentGateway(RedirectUrls redirects) {
        this.redirects = redirects;
    }

    @Override
    public URI checkoutUrl(Transaction transaction) {
        return URI.create(redirects.transactionPage(transaction.id()));
    }
}

package co.edu.corhuila.marketagri.transactions.domain.model;

/** I-06 row 4: the adapter behind {@code PaymentGateway} could not build the checkout (missing configuration). */
public class PaymentGatewayException extends DomainException {

    public PaymentGatewayException(String message) {
        super("PAYMENT_GATEWAY_ERROR", message);
    }
}

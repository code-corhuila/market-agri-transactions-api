package co.edu.corhuila.marketagri.transactions.domain.model;

/** The provider gave no answer about a payment (network, an error or no key): the payment stays PENDING. */
public class PaymentLookupException extends RuntimeException {

    public PaymentLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}

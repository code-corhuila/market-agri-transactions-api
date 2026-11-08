package co.edu.corhuila.marketagri.transactions.domain.model;

/**
 * An approved result that does not match its payment (E-43, guards before CONFIRMED): another
 * amount or currency. Nothing changes and the webhook still answers 200; it is a security warning.
 */
public class PaymentResultMismatchException extends RuntimeException {

    public PaymentResultMismatchException(String message) {
        super(message);
    }
}

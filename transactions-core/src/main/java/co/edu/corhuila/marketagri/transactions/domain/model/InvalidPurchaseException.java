package co.edu.corhuila.marketagri.transactions.domain.model;

/** A quantity, price or amount that breaks I-06 ({@code 400}); the HTTP adapter checks first. */
public class InvalidPurchaseException extends DomainException {

    public InvalidPurchaseException(String message) {
        super("VALIDATION_ERROR", message);
    }
}

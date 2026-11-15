package co.edu.corhuila.marketagri.transactions.domain.model;

/**
 * A broken business rule, with the code the contract answers for it (I-06: {@code TRANSACTION_MISMATCH}…).
 * The HTTP adapter turns the code into the status and the Spanish message; this message is for logs.
 */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

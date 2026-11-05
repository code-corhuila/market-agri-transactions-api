package co.edu.corhuila.marketagri.transactions.adapter.in.http;

/** A query parameter or header that is out of range: {@code 400 VALIDATION_ERROR} naming it (D-C24). */
final class InvalidParameterException extends RuntimeException {

    private final String field;

    InvalidParameterException(String field, String message) {
        super(message);
        this.field = field;
    }

    String field() {
        return field;
    }
}

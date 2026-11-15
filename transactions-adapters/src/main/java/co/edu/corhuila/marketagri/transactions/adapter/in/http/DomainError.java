package co.edu.corhuila.marketagri.transactions.adapter.in.http;

import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/** Codes of the transactions rules (api-contract §4.4: E-41, E-43, I-06), with their status and Spanish message. */
enum DomainError {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.message()),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "La transacción no existe."),
    NOT_A_PARTICIPANT(HttpStatus.FORBIDDEN, "No participa en esta transacción."),
    TRANSACTION_MISMATCH(HttpStatus.CONFLICT, "La transacción ya existe con otros datos."),
    TRANSACTION_IN_PROGRESS(HttpStatus.CONFLICT, "Ya hay un pago en curso para esta conversación."),
    PAYMENT_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "No fue posible iniciar el pago. Intente de nuevo."),
    INVALID_SIGNATURE(HttpStatus.BAD_REQUEST, "Firma inválida.");

    private final HttpStatus status;
    private final String message;

    DomainError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    HttpStatus status() {
        return status;
    }

    String message() {
        return message;
    }

    static Optional<DomainError> of(String code) {
        return Arrays.stream(values()).filter(error -> error.name().equals(code)).findFirst();
    }
}

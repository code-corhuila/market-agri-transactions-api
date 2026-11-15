package co.edu.corhuila.marketagri.transactions.adapter.in.http;

import co.edu.corhuila.marketagri.transactions.application.port.in.CreatePayment;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of I-06: the snapshot of the conversation (I-05) and of the reservation (I-03). Every rule
 * of the card is a {@code 400 VALIDATION_ERROR} with the field named; the names fit the columns
 * of B2 (1 to 150 characters).
 */
public record CreatePaymentRequest(
        @NotNull(message = REQUIRED) UUID transactionId,
        @NotNull(message = REQUIRED) UUID conversationId,
        @NotNull(message = REQUIRED) UUID buyerId,
        @NotNull(message = REQUIRED) UUID producerId,
        @NotNull(message = REQUIRED) UUID productId,
        @NotBlank(message = REQUIRED) @Size(max = NAME_MAX, message = NAME_SIZE) String buyerName,
        @NotBlank(message = REQUIRED) @Size(max = NAME_MAX, message = NAME_SIZE) String producerName,
        @NotBlank(message = REQUIRED) @Size(max = NAME_MAX, message = NAME_SIZE) String productName,
        @NotNull(message = REQUIRED)
        @Positive(message = "Debe ser mayor que cero.")
        @Digits(integer = 10, fraction = 2, message = "Debe ser un número con máximo dos decimales.")
        BigDecimal quantity,
        @NotNull(message = REQUIRED) ProductUnit unit,
        @NotNull(message = REQUIRED)
        @Positive(message = "Debe ser mayor que cero.")
        @Max(value = 999_999_999_999L, message = "Supera el máximo permitido.")
        Long unitPriceCents) {

    static final String REQUIRED = "Campo obligatorio.";
    static final int NAME_MAX = 150;
    static final String NAME_SIZE = "Máximo 150 caracteres.";

    CreatePayment.Command toCommand(UUID callerId, String idempotencyKey) {
        return new CreatePayment.Command(callerId, idempotencyKey, transactionId, conversationId, buyerId,
                buyerName.strip(), producerId, producerName.strip(), productId, productName.strip(), quantity, unit,
                unitPriceCents);
    }
}

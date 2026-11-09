package co.edu.corhuila.marketagri.transactions.adapter.out.payment;

import co.edu.corhuila.marketagri.transactions.domain.model.PaymentMethod;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentOutcome;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.Set;

/**
 * A Wompi transaction object (the event's {@code data.transaction} and the API's {@code data})
 * as a result of the core: {@code APPROVED}; {@code DECLINED}, {@code VOIDED}, {@code ERROR} →
 * rejected; anything else pending; a method outside the contract → {@code OTHER} (E-43).
 */
public final class WompiResults {

    private static final Set<String> REJECTED = Set.of("DECLINED", "VOIDED", "ERROR");
    private static final Set<String> CONTRACT_METHODS =
            Set.of(Arrays.stream(PaymentMethod.values()).map(Enum::name).toArray(String[]::new));

    private WompiResults() {
    }

    public static PaymentResult of(JsonNode transaction) {
        return new PaymentResult(transaction.path("id").asText(), outcome(transaction.path("status").asText()),
                transaction.path("amount_in_cents").asLong(), transaction.path("currency").asText(),
                method(transaction.path("payment_method_type").asText(null)));
    }

    private static PaymentOutcome outcome(String status) {
        if ("APPROVED".equals(status)) {
            return PaymentOutcome.APPROVED;
        }
        return REJECTED.contains(status) ? PaymentOutcome.REJECTED : PaymentOutcome.PENDING;
    }

    private static PaymentMethod method(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return CONTRACT_METHODS.contains(type) ? PaymentMethod.valueOf(type) : PaymentMethod.OTHER;
    }
}

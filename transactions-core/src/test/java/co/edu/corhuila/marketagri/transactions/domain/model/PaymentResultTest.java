package co.edu.corhuila.marketagri.transactions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** A verified result of the provider: always with its payment id (1–64 characters, as the column allows). */
class PaymentResultTest {

    @Test
    void aResultNeedsTheProviderPaymentId() {
        assertThatThrownBy(() -> new PaymentResult(" ", PaymentOutcome.APPROVED, 100, "COP", PaymentMethod.CARD))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentResult("x".repeat(65), PaymentOutcome.APPROVED, 100, "COP", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentResult("w-1", null, 100, "COP", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void theMethodMayBeUnknownOnAFailure() {
        PaymentResult result = new PaymentResult("x".repeat(64), PaymentOutcome.REJECTED, 100, "COP", null);

        assertThat(result.paymentMethod()).isNull();
    }
}

package co.edu.corhuila.marketagri.transactions.adapter.out.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Quantity;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The fake provider (ADR-019) sends the buyer to the transaction page of the interface. */
class FakePaymentGatewayTest {

    private static final UUID ID = UUID.fromString("7a1f0c2e-0000-4000-8000-000000000001");

    private static Transaction transaction() {
        return Transaction.open(ID, new PurchaseSnapshot(UUID.randomUUID(), UUID.randomUUID(), "Carlos Pérez",
                UUID.randomUUID(), "Ana Ruiz", UUID.randomUUID(), "Café", Quantity.of("1"), ProductUnit.ARROBA,
                Money.ofCents(100)), Instant.parse("2026-11-04T15:00:00Z"));
    }

    @Test
    void theLinkIsTheTransactionPage() {
        assertThat(new FakePaymentGateway(new RedirectUrls("http://localhost:8001/")).checkoutUrl(transaction()))
                .isEqualTo(URI.create("http://localhost:8001/transacciones/" + ID));
    }

    @Test
    void withoutAPublicBaseUrlNothingStarts() {
        assertThatThrownBy(() -> new RedirectUrls(" ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PUBLIC_BASE_URL");
    }
}

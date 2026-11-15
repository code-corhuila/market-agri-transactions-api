package co.edu.corhuila.marketagri.transactions.adapter.out.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentGatewayException;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Quantity;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** ADR-019 Web Checkout: the link and its integrity signature, built with no call to Wompi (AC2). */
class WompiPaymentGatewayTest {

    private static final UUID ID = UUID.fromString("7a1f0c2e-0000-4000-8000-000000000001");
    private static final RedirectUrls REDIRECTS = new RedirectUrls("http://localhost:8001");

    /** 12.5 × 680 000 = 8 500 000 centavos, created 15:00:00 → expires 15:30:00.000Z. */
    private static Transaction transaction() {
        PurchaseSnapshot purchase = new PurchaseSnapshot(UUID.randomUUID(), UUID.randomUUID(), "Carlos Pérez",
                UUID.randomUUID(), "Ana Ruiz", UUID.randomUUID(), "Café pergamino seco", Quantity.of("12.5"),
                ProductUnit.ARROBA, Money.ofCents(680_000));
        return Transaction.open(ID, purchase, Instant.parse("2026-11-04T15:00:00.987Z"));
    }

    private static Map<String, String> query(URI link) {
        return Arrays.stream(link.getRawQuery().split("&")).map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }

    @Test
    void theSignatureFollowsWompisDocumentedExample() {
        // "Widget & Web Checkout", step 3: the chain without expiration and the hash it shows.
        assertThat(WompiPaymentGateway.signature(
                "sk8-438k4-xmxm392-sn2m2490000COPprod_integrity_Z5mMke9x0k8gpErbDqwrJXMqsI6SFli6"))
                .isEqualTo("37c8407747e595535433ef8f6a811d853cd943046624a0ec04662b17bbf33bf5");
    }

    @Test
    void theLinkCarriesEveryFieldOfTheCheckoutAndTheSignatureComputedByHand() {
        URI link = new WompiPaymentGateway("pub_test_vector", "test_integrity_vector", REDIRECTS)
                .checkoutUrl(transaction());

        assertThat(link.toString()).startsWith("https://checkout.wompi.co/p/?");
        assertThat(query(link))
                .containsEntry("public-key", "pub_test_vector")
                .containsEntry("currency", "COP")
                .containsEntry("amount-in-cents", "8500000")
                .containsEntry("reference", ID.toString())
                .containsEntry("expiration-time", "2026-11-04T15:30:00.000Z")
                .containsEntry("redirect-url", "http://localhost:8001/transacciones/" + ID)
                // sha256sum of "7a1f0c2e-0000-4000-8000-0000000000018500000COP2026-11-04T15:30:00.000Ztest_integrity_vector"
                .containsEntry("signature:integrity", "87cc8499316fe2a6928d6fa8aee8a816e5e469f6e96ec6f79fab9988e2f61a4f")
                .hasSize(7);
    }

    @Test
    void theSameTransactionAlwaysGivesTheSameLink() {
        WompiPaymentGateway gateway = new WompiPaymentGateway("pub_test_vector", "test_integrity_vector", REDIRECTS);

        assertThat(gateway.checkoutUrl(transaction())).isEqualTo(gateway.checkoutUrl(transaction()));
    }

    @Test
    void withoutItsKeysTheCheckoutCannotBeBuilt() {
        assertThatThrownBy(() -> new WompiPaymentGateway("", "test_integrity_vector", REDIRECTS).checkoutUrl(transaction()))
                .isInstanceOf(PaymentGatewayException.class);
        assertThatThrownBy(() -> new WompiPaymentGateway("pub_test_vector", null, REDIRECTS).checkoutUrl(transaction()))
                .isInstanceOf(PaymentGatewayException.class);
    }
}

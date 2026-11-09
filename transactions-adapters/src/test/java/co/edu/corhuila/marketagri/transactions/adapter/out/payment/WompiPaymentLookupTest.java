package co.edu.corhuila.marketagri.transactions.adapter.out.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.marketagri.transactions.domain.model.PaymentLookupException;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentMethod;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentOutcome;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The query of the expiry job (Wompi's API reference: {@code GET /transactions?reference=…},
 * private key as Bearer), against a local HTTP server that plays Wompi's sandbox.
 */
class WompiPaymentLookupTest {

    private static final UUID TX = UUID.fromString("7a1f0c2e-0000-4000-8000-000000000001");

    private HttpServer server;
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    private String wompi(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/transactions", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static String transaction(String id, String status) {
        return """
                {"id":"%s","reference":"%s","amount_in_cents":8500000,"currency":"COP",
                 "payment_method_type":"NEQUI","status":"%s"}""".formatted(id, TX, status);
    }

    private WompiPaymentLookup lookup(String apiUrl) {
        return new WompiPaymentLookup(apiUrl, "prv_test_only", Duration.ofSeconds(3));
    }

    @Test
    void theApprovedPaymentOfTheReferenceWinsAndThePrivateKeyAuthenticates() throws Exception {
        String api = wompi(200, "{\"data\":[" + transaction("w-1", "DECLINED") + "," + transaction("w-2", "APPROVED") + "]}");

        assertThat(lookup(api).findByReference(TX)).hasValueSatisfying(result -> {
            assertThat(result.outcome()).isEqualTo(PaymentOutcome.APPROVED);
            assertThat(result.gatewayTransactionId()).isEqualTo("w-2");
            assertThat(result.amountCents()).isEqualTo(8_500_000);
            assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.NEQUI);
        });
        assertThat(query.get()).isEqualTo("reference=" + TX);
        assertThat(authorization.get()).isEqualTo("Bearer prv_test_only");
    }

    @Test
    void aRejectedPaymentIsReturnedWhenNoneWasApproved() throws Exception {
        String api = wompi(200, "{\"data\":[" + transaction("w-1", "PENDING") + "," + transaction("w-2", "VOIDED") + "]}");

        assertThat(lookup(api).findByReference(TX))
                .hasValueSatisfying(result -> assertThat(result.outcome()).isEqualTo(PaymentOutcome.REJECTED));
    }

    @Test
    void noPaymentForTheReferenceIsEmpty() throws Exception {
        assertThat(lookup(wompi(200, "{\"data\":[]}")).findByReference(TX)).isEmpty();
    }

    @Test
    void anErrorOfWompiOrNoKeyIsNoAnswer() throws Exception {
        String api = wompi(401, "{\"error\":{}}");

        assertThatThrownBy(() -> lookup(api).findByReference(TX)).isInstanceOf(PaymentLookupException.class);
        assertThatThrownBy(() -> new WompiPaymentLookup(api, "", Duration.ofSeconds(1)).findByReference(TX))
                .isInstanceOf(PaymentLookupException.class);
        assertThatThrownBy(() -> lookup("http://127.0.0.1:1/v1").findByReference(TX))
                .isInstanceOf(PaymentLookupException.class);
    }
}

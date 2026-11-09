package co.edu.corhuila.marketagri.transactions.adapter.out.payment;

import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentLookup;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentLookupException;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentOutcome;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Wompi's {@code GET /transactions?reference=…} with the private key as Bearer (Wompi's API reference).
 * An approved payment wins over a rejected one, and a rejected one over a pending one.
 */
public final class WompiPaymentLookup implements PaymentLookup {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int OK = 200;

    private final String apiUrl;
    private final String privateKey;
    private final Duration timeout;
    private final HttpClient http;

    public WompiPaymentLookup(String apiUrl, String privateKey, Duration timeout) {
        this.apiUrl = apiUrl;
        this.privateKey = privateKey;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public Optional<PaymentResult> findByReference(UUID transactionId) {
        if (privateKey == null || privateKey.isBlank()) {
            throw new PaymentLookupException("WOMPI_PRIVATE_KEY is not set", null);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + "/transactions?reference=" + transactionId))
                .header("Authorization", "Bearer " + privateKey)
                .timeout(timeout)
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != OK) {
                throw new PaymentLookupException("Wompi answered " + response.statusCode() + " for " + transactionId, null);
            }
            List<PaymentResult> results = new ArrayList<>();
            JSON.readTree(response.body()).path("data").forEach(transaction -> results.add(WompiResults.of(transaction)));
            return results.stream().min(Comparator.comparing(WompiPaymentLookup::rank));
        } catch (PaymentLookupException known) {
            throw known;
        } catch (IOException | InterruptedException | RuntimeException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new PaymentLookupException("no answer from Wompi for " + transactionId, failure);
        }
    }

    private static int rank(PaymentResult result) {
        return switch (result.outcome()) {
            case APPROVED -> 0;
            case REJECTED -> 1;
            case PENDING -> 2;
        };
    }
}

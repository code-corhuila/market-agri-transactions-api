package co.edu.corhuila.marketagri.transactions.adapter.out.payment;

import java.util.UUID;

/**
 * Where the buyer comes back after paying: the transaction page of the interface,
 * {@code <PUBLIC_BASE_URL>/transacciones/<id>} (transactions-portal). Wompi appends {@code ?id=}
 * with its own transaction id; the page reads the state from E-41, never from that parameter.
 */
public record RedirectUrls(String publicBaseUrl) {

    public RedirectUrls {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalArgumentException("Set PUBLIC_BASE_URL");
        }
        publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    public String transactionPage(UUID transactionId) {
        return publicBaseUrl + "/transacciones/" + transactionId;
    }
}

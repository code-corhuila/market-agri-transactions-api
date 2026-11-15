package co.edu.corhuila.marketagri.transactions.adapter.out.payment;

import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentGateway;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentGatewayException;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Wompi Web Checkout (ADR-019): the link is built here, with no call to Wompi. The integrity
 * signature is {@code SHA-256(reference + amountInCents + currency + expirationTime + secret)} in
 * lowercase hex, the order of Wompi's documentation ("Widget & Web Checkout"); the secret never
 * leaves the backend. A missing key is {@code PAYMENT_GATEWAY_ERROR} (I-06 row 4).
 */
public final class WompiPaymentGateway implements PaymentGateway {

    static final String CHECKOUT = "https://checkout.wompi.co/p/";
    static final String CURRENCY = "COP";
    /** ISO 8601 in UTC with milliseconds, as Wompi's example: {@code 2023-06-09T20:28:50.000Z}. */
    static final DateTimeFormatter EXPIRATION = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final String publicKey;
    private final String integritySecret;
    private final RedirectUrls redirects;

    public WompiPaymentGateway(String publicKey, String integritySecret, RedirectUrls redirects) {
        this.publicKey = publicKey;
        this.integritySecret = integritySecret;
        this.redirects = redirects;
    }

    @Override
    public URI checkoutUrl(Transaction transaction) {
        if (isBlank(publicKey) || isBlank(integritySecret)) {
            throw new PaymentGatewayException("WOMPI_PUBLIC_KEY or WOMPI_INTEGRITY_SECRET is not set");
        }
        String reference = transaction.id().toString();
        String amount = Long.toString(transaction.amount().cents());
        String expiration = EXPIRATION.format(transaction.expiresAt());
        Map<String, String> query = new LinkedHashMap<>();
        query.put("public-key", publicKey);
        query.put("currency", CURRENCY);
        query.put("amount-in-cents", amount);
        query.put("reference", reference);
        query.put("expiration-time", expiration);
        query.put("redirect-url", redirects.transactionPage(transaction.id()));
        query.put("signature:integrity", signature(reference + amount + CURRENCY + expiration + integritySecret));
        return URI.create(CHECKOUT + "?" + query.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&")));
    }

    static String signature(String chain) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(chain.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("every JVM has SHA-256", impossible);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

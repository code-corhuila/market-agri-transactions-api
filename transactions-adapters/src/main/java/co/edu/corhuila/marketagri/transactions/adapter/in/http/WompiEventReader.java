package co.edu.corhuila.marketagri.transactions.adapter.in.http;

import co.edu.corhuila.marketagri.transactions.application.port.in.ApplyPaymentResult.Command;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentMethod;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentOutcome;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Wompi's events (ADR-019, Wompi's documentation "Eventos"). The checksum is
 * {@code SHA-256(values of signature.properties in their order + timestamp + events secret)}; it
 * comes in {@code signature.checksum} and in {@code X-Event-Checksum}, compared ignoring case
 * (E-43). Without the secret no event is valid.
 */
public final class WompiEventReader {

    static final String TRANSACTION_UPDATED = "transaction.updated";
    private static final Set<String> REJECTED = Set.of("DECLINED", "VOIDED", "ERROR");
    private static final Set<String> CONTRACT_METHODS =
            Set.of(Arrays.stream(PaymentMethod.values()).map(Enum::name).toArray(String[]::new));

    private final String eventsSecret;

    public WompiEventReader(String eventsSecret) {
        this.eventsSecret = eventsSecret == null ? "" : eventsSecret;
    }

    /** True only when every signed property exists and the chain hashes to the received checksum. */
    public boolean signatureValid(JsonNode event, String headerChecksum) {
        JsonNode properties = event.at("/signature/properties");
        JsonNode timestamp = event.get("timestamp");
        String received = event.at("/signature/checksum").asText("");
        if (received.isBlank()) {
            received = headerChecksum == null ? "" : headerChecksum;
        }
        if (eventsSecret.isBlank() || received.isBlank() || !properties.isArray() || properties.isEmpty()
                || timestamp == null || !timestamp.canConvertToLong()) {
            return false;
        }
        StringBuilder chain = new StringBuilder();
        for (JsonNode property : properties) {
            JsonNode value = event.at("/data/" + property.asText().replace('.', '/'));
            if (value.isMissingNode() || value.isNull() || value.isContainerNode()) {
                return false;
            }
            chain.append(value.asText());
        }
        chain.append(timestamp.asLong()).append(eventsSecret);
        byte[] expected = sha256(chain.toString()).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * The result of a {@code transaction.updated} whose reference is one of our ids; empty for any
     * other event, which E-43 acknowledges and ignores.
     */
    public Optional<Command> command(JsonNode event, String correlationId) {
        JsonNode transaction = event.at("/data/transaction");
        if (!TRANSACTION_UPDATED.equals(event.path("event").asText()) || !transaction.isObject()) {
            return Optional.empty();
        }
        UUID reference;
        try {
            reference = UUID.fromString(transaction.path("reference").asText());
        } catch (IllegalArgumentException notOurs) {
            return Optional.empty();
        }
        PaymentResult result = new PaymentResult(transaction.path("id").asText(), outcome(transaction.path("status").asText()),
                transaction.path("amount_in_cents").asLong(), transaction.path("currency").asText(),
                method(transaction.path("payment_method_type").asText(null)));
        return Optional.of(new Command(reference, result, correlationId));
    }

    private static PaymentOutcome outcome(String status) {
        if ("APPROVED".equals(status)) {
            return PaymentOutcome.APPROVED;
        }
        return REJECTED.contains(status) ? PaymentOutcome.REJECTED : PaymentOutcome.PENDING;
    }

    /** {@code OTHER} for a method outside the contract's enum (E-43). */
    private static PaymentMethod method(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return CONTRACT_METHODS.contains(type) ? PaymentMethod.valueOf(type) : PaymentMethod.OTHER;
    }

    private static String sha256(String chain) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(chain.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("every JVM has SHA-256", impossible);
        }
    }
}

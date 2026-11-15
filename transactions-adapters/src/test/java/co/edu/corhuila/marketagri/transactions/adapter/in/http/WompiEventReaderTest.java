package co.edu.corhuila.marketagri.transactions.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.marketagri.transactions.application.port.in.ApplyPaymentResult.Command;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentMethod;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Wompi's {@code transaction.updated} (ADR-019, E-43): the checksum is the authentication, and the
 * event becomes a result in the terms of the core. The test vector is the chain of the example in
 * Wompi's documentation ("Eventos"): properties in their order, then the timestamp, then the secret.
 */
class WompiEventReaderTest {

    private static final String SECRET = "prod_events_OcHnIzeBl5socpwByQ4hA52Em3USQ93Z";
    private static final String CHECKSUM = "5a18ec5e8fdb7df463e9f94774cba8f583ba21bd04a09ceff2ea68a4bc0aefbe";
    private static final UUID TX = UUID.fromString("7a1f0c2e-0000-4000-8000-000000000001");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WompiEventReader reader = new WompiEventReader(SECRET);

    static String event(String reference, String status, String method, String checksum) {
        return """
                {"event":"transaction.updated",
                 "data":{"transaction":{"id":"1234-1610641025-49201","amount_in_cents":4490000,
                   "reference":"%s","customer_email":"juan@example.com","currency":"COP",
                   "payment_method_type":"%s","redirect_url":"https://example.com","status":"%s",
                   "shipping_address":null,"payment_link_id":null,"payment_source_id":null}},
                 "environment":"test",
                 "signature":{"properties":["transaction.id","transaction.status","transaction.amount_in_cents"],
                   "checksum":"%s"},
                 "timestamp":1530291411,"sent_at":"2018-07-20T16:45:05.000Z"}""".formatted(reference, method, status, checksum);
    }

    private static JsonNode json(String body) throws Exception {
        return JSON.readTree(body);
    }

    @Test
    void theDocumentedChainSignsTheEvent() throws Exception {
        assertThat(reader.signatureValid(json(event(TX.toString(), "APPROVED", "NEQUI", CHECKSUM)), null)).isTrue();
    }

    @Test
    void theChecksumIsComparedIgnoringCase() throws Exception {
        assertThat(reader.signatureValid(json(event(TX.toString(), "APPROVED", "NEQUI", CHECKSUM.toUpperCase())), null))
                .isTrue();
    }

    @Test
    void aTamperedSignedFieldBreaksTheSignature() throws Exception {
        assertThat(reader.signatureValid(json(event(TX.toString(), "DECLINED", "NEQUI", CHECKSUM)), null)).isFalse();
        assertThat(reader.signatureValid(json(event(TX.toString(), "APPROVED", "NEQUI", CHECKSUM)
                .replace("4490000", "4490001")), null)).isFalse();
    }

    @Test
    void theHeaderIsUsedWhenTheBodyHasNoChecksum() throws Exception {
        JsonNode unsigned = json(event(TX.toString(), "APPROVED", "NEQUI", ""));

        assertThat(reader.signatureValid(unsigned, CHECKSUM)).isTrue();
        assertThat(reader.signatureValid(unsigned, null)).isFalse();
        assertThat(reader.signatureValid(unsigned, "0".repeat(64))).isFalse();
    }

    @Test
    void withoutTheSecretNothingIsValid() throws Exception {
        assertThat(new WompiEventReader("").signatureValid(json(event(TX.toString(), "APPROVED", "NEQUI", CHECKSUM)),
                CHECKSUM)).isFalse();
    }

    @Test
    void aMalformedEventIsNotValid() throws Exception {
        assertThat(reader.signatureValid(json("{\"event\":\"transaction.updated\"}"), CHECKSUM)).isFalse();
        assertThat(reader.signatureValid(json(event(TX.toString(), "APPROVED", "NEQUI", CHECKSUM)
                .replace("\"transaction.id\"", "\"transaction.missing\"")), null)).isFalse();
    }

    @Test
    void anApprovedEventBecomesAnApprovedResultForItsReference() throws Exception {
        Optional<Command> command = reader.command(json(event(TX.toString(), "APPROVED", "NEQUI", CHECKSUM)), "corr-1");

        assertThat(command).hasValueSatisfying(it -> {
            assertThat(it.transactionId()).isEqualTo(TX);
            assertThat(it.correlationId()).isEqualTo("corr-1");
            assertThat(it.result().outcome()).isEqualTo(PaymentOutcome.APPROVED);
            assertThat(it.result().gatewayTransactionId()).isEqualTo("1234-1610641025-49201");
            assertThat(it.result().amountCents()).isEqualTo(4_490_000);
            assertThat(it.result().currency()).isEqualTo("COP");
            assertThat(it.result().paymentMethod()).isEqualTo(PaymentMethod.NEQUI);
        });
    }

    @Test
    void declinedVoidedAndErrorAreRejectionsAndPendingIsPending() throws Exception {
        for (String status : new String[] {"DECLINED", "VOIDED", "ERROR"}) {
            assertThat(reader.command(json(event(TX.toString(), status, "CARD", CHECKSUM)), "c"))
                    .hasValueSatisfying(it -> assertThat(it.result().outcome()).isEqualTo(PaymentOutcome.REJECTED));
        }
        assertThat(reader.command(json(event(TX.toString(), "PENDING", "CARD", CHECKSUM)), "c"))
                .hasValueSatisfying(it -> assertThat(it.result().outcome()).isEqualTo(PaymentOutcome.PENDING));
    }

    @Test
    void aMethodOutsideTheContractIsOther() throws Exception {
        assertThat(reader.command(json(event(TX.toString(), "APPROVED", "BANCOLOMBIA_COLLECT", CHECKSUM)), "c"))
                .hasValueSatisfying(it -> assertThat(it.result().paymentMethod()).isEqualTo(PaymentMethod.OTHER));
        assertThat(reader.command(json(event(TX.toString(), "APPROVED", "PSE", CHECKSUM)), "c"))
                .hasValueSatisfying(it -> assertThat(it.result().paymentMethod()).isEqualTo(PaymentMethod.PSE));
    }

    @Test
    void anotherEventTypeOrAReferenceThatIsNotOursIsIgnored() throws Exception {
        assertThat(reader.command(json(event(TX.toString(), "APPROVED", "NEQUI", CHECKSUM)
                .replace("transaction.updated", "nequi_token.updated")), "c")).isEmpty();
        assertThat(reader.command(json(event("ORDER-77", "APPROVED", "NEQUI", CHECKSUM)), "c")).isEmpty();
    }
}

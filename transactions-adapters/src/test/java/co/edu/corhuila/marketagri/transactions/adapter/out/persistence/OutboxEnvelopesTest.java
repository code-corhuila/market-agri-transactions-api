package co.edu.corhuila.marketagri.transactions.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.marketagri.transactions.domain.model.FailureReason;
import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentMethod;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentOutcome;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentResult;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Quantity;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The envelope of 02-domain/domain-events.md with the payloads of api-contract §6, as the outbox keeps them. */
class OutboxEnvelopesTest {

    private static final UUID EVENT = UUID.fromString("e0000000-0000-4000-8000-000000000001");
    private static final UUID TX = UUID.fromString("7a1f0c2e-0000-4000-8000-000000000001");
    private static final UUID CONVERSATION = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID BUYER = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID PRODUCER = UUID.fromString("a1b2c3d4-4444-0000-0000-000000000001");
    private static final UUID PRODUCT = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final Instant LATER = Instant.parse("2026-11-04T15:07:30Z");

    private final OutboxEnvelopes envelopes = new OutboxEnvelopes(new ObjectMapper());

    private static Transaction pending() {
        PurchaseSnapshot purchase = new PurchaseSnapshot(CONVERSATION, BUYER, "Carlos Pérez", PRODUCER, "Ana Ruiz",
                PRODUCT, "Café pergamino seco", Quantity.of("12.5"), ProductUnit.KILOGRAMO, Money.ofCents(680_000));
        return Transaction.open(TX, purchase, Instant.parse("2026-11-04T15:00:00Z"));
    }

    private static PaymentResult result(PaymentOutcome outcome) {
        return new PaymentResult("w-1", outcome, 8_500_000, "COP", PaymentMethod.CARD);
    }

    @Test
    void transactionConfirmedCarriesTheEnvelopeAndThePayloadOfTheContract() throws Exception {
        Transaction confirmed = pending().confirm(result(PaymentOutcome.APPROVED), LATER);
        JsonNode envelope = new ObjectMapper().readTree(
                envelopes.json(TransactionEvent.confirmed(EVENT, confirmed, "corr-1", "w-1")));

        assertThat(envelope.get("eventId").asText()).isEqualTo(EVENT.toString());
        assertThat(envelope.get("eventType").asText()).isEqualTo("TransactionConfirmed");
        assertThat(envelope.get("aggregateId").asText()).isEqualTo(TX.toString());
        assertThat(envelope.get("aggregateType").asText()).isEqualTo("Transaction");
        assertThat(envelope.get("occurredAt").asText()).isEqualTo("2026-11-04T15:07:30Z");
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.at("/metadata/correlationId").asText()).isEqualTo("corr-1");
        assertThat(envelope.at("/metadata/causationId").asText()).isEqualTo("w-1");
        assertThat(envelope.at("/metadata/userId").asText()).isEqualTo(BUYER.toString());

        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("transactionId").asText()).isEqualTo(TX.toString());
        assertThat(payload.get("conversationId").asText()).isEqualTo(CONVERSATION.toString());
        assertThat(payload.get("productId").asText()).isEqualTo(PRODUCT.toString());
        assertThat(payload.get("productName").asText()).isEqualTo("Café pergamino seco");
        assertThat(payload.get("buyerId").asText()).isEqualTo(BUYER.toString());
        assertThat(payload.get("producerId").asText()).isEqualTo(PRODUCER.toString());
        assertThat(payload.get("quantity").decimalValue()).isEqualByComparingTo("12.5");
        assertThat(payload.get("unit").asText()).isEqualTo("KILOGRAMO");
        assertThat(payload.get("unitPriceCents").asLong()).isEqualTo(680_000);
        assertThat(payload.get("amountCents").asLong()).isEqualTo(8_500_000);
        assertThat(payload.get("currency").asText()).isEqualTo("COP");
        assertThat(payload.get("confirmedAt").asText()).isEqualTo("2026-11-04T15:07:30Z");
    }

    @Test
    void transactionFailedCarriesItsReason() throws Exception {
        Transaction failed = pending().fail(result(PaymentOutcome.REJECTED), LATER);
        JsonNode envelope = new ObjectMapper().readTree(envelopes.json(
                TransactionEvent.failed(EVENT, failed, FailureReason.PAYMENT_FAILED, "corr-1", "w-1")));

        assertThat(envelope.get("eventType").asText()).isEqualTo("TransactionFailed");
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("transactionId").asText()).isEqualTo(TX.toString());
        assertThat(payload.get("conversationId").asText()).isEqualTo(CONVERSATION.toString());
        assertThat(payload.get("productId").asText()).isEqualTo(PRODUCT.toString());
        assertThat(payload.get("quantity").decimalValue()).isEqualByComparingTo("12.5");
        assertThat(payload.get("reason").asText()).isEqualTo("PAYMENT_FAILED");
        assertThat(payload.get("failedAt").asText()).isEqualTo("2026-11-04T15:07:30Z");
        assertThat(payload.has("amountCents")).isFalse();
    }

    @Test
    void eachEventHasTheRoutingKeyOfTheContract() {
        assertThat(OutboxEnvelopes.routingKey(TransactionEvent.Type.TRANSACTION_CONFIRMED))
                .isEqualTo("transactions.transaction-confirmed");
        assertThat(OutboxEnvelopes.routingKey(TransactionEvent.Type.TRANSACTION_FAILED))
                .isEqualTo("transactions.transaction-failed");
    }
}

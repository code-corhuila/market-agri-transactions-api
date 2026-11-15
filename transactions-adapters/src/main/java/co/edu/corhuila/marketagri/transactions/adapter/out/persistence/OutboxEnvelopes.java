package co.edu.corhuila.marketagri.transactions.adapter.out.persistence;

import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;

/**
 * The envelope of {@code 02-domain/domain-events.md} with the payloads of api-contract §6, built
 * once when the event is written to the outbox; the relay publishes it as it is. Instants in ISO 8601.
 */
public final class OutboxEnvelopes {

    private static final int VERSION = 1;
    private static final String AGGREGATE_TYPE = "Transaction";

    private final ObjectMapper json;

    public OutboxEnvelopes(ObjectMapper json) {
        this.json = json;
    }

    /** api-contract §6: the routing key of each event on {@code market-agri.events}. */
    public static String routingKey(TransactionEvent.Type type) {
        return switch (type) {
            case TRANSACTION_CONFIRMED -> "transactions.transaction-confirmed";
            case TRANSACTION_FAILED -> "transactions.transaction-failed";
        };
    }

    public String json(TransactionEvent event) {
        Transaction transaction = event.transaction();
        ObjectNode envelope = json.createObjectNode();
        envelope.put("eventId", event.eventId().toString());
        envelope.put("eventType", event.type().eventType());
        envelope.put("aggregateId", transaction.id().toString());
        envelope.put("aggregateType", AGGREGATE_TYPE);
        envelope.put("occurredAt", iso(event.occurredAt()));
        envelope.put("version", VERSION);
        envelope.set("payload", payload(event));
        ObjectNode metadata = envelope.putObject("metadata");
        metadata.put("correlationId", event.correlationId());
        metadata.put("causationId", event.causationId());
        metadata.put("userId", transaction.purchase().buyerId().toString());
        try {
            return json.writeValueAsString(envelope);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("a tree of plain values always serializes", impossible);
        }
    }

    private ObjectNode payload(TransactionEvent event) {
        Transaction transaction = event.transaction();
        ObjectNode payload = json.createObjectNode();
        payload.put("transactionId", transaction.id().toString());
        payload.put("conversationId", transaction.purchase().conversationId().toString());
        payload.put("productId", transaction.purchase().productId().toString());
        if (event.type() == TransactionEvent.Type.TRANSACTION_FAILED) {
            payload.put("quantity", transaction.purchase().quantity().value());
            payload.put("reason", event.reason().name());
            payload.put("failedAt", iso(transaction.failedAt()));
            return payload;
        }
        payload.put("productName", transaction.purchase().productName());
        payload.put("buyerId", transaction.purchase().buyerId().toString());
        payload.put("producerId", transaction.purchase().producerId().toString());
        payload.put("quantity", transaction.purchase().quantity().value());
        payload.put("unit", transaction.purchase().unit().name());
        payload.put("unitPriceCents", transaction.purchase().unitPrice().cents());
        payload.put("amountCents", transaction.amount().cents());
        payload.put("currency", Transaction.CURRENCY);
        payload.put("confirmedAt", iso(transaction.confirmedAt()));
        return payload;
    }

    private static String iso(Instant instant) {
        return instant.toString();
    }
}

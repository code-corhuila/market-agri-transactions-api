package co.edu.corhuila.marketagri.transactions.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An event of a settled payment, written to the outbox in the same transaction as the state
 * change (ADR-014) and published later by the relay. The envelope and the payload of api-contract
 * §6 are built from it by the outbound adapter. {@code causationId} is the provider payment id.
 */
public record TransactionEvent(UUID eventId, Type type, Transaction transaction, FailureReason reason,
                               Instant occurredAt, String correlationId, String causationId) {

    /** The two events transactions publishes, with the name the envelope carries. */
    public enum Type {
        TRANSACTION_CONFIRMED("TransactionConfirmed"),
        TRANSACTION_FAILED("TransactionFailed");

        private final String eventType;

        Type(String eventType) {
            this.eventType = eventType;
        }

        public String eventType() {
            return eventType;
        }
    }

    public TransactionEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static TransactionEvent confirmed(UUID eventId, Transaction confirmed, String correlationId,
                                             String causationId) {
        if (confirmed.status() != TransactionStatus.CONFIRMED) {
            throw new IllegalArgumentException("TransactionConfirmed of a " + confirmed.status() + " payment");
        }
        return new TransactionEvent(eventId, Type.TRANSACTION_CONFIRMED, confirmed, null,
                confirmed.confirmedAt(), correlationId, causationId);
    }

    public static TransactionEvent failed(UUID eventId, Transaction failed, FailureReason reason,
                                          String correlationId, String causationId) {
        Objects.requireNonNull(reason, "reason");
        if (failed.status() != TransactionStatus.FAILED) {
            throw new IllegalArgumentException("TransactionFailed of a " + failed.status() + " payment");
        }
        return new TransactionEvent(eventId, Type.TRANSACTION_FAILED, failed, reason, failed.failedAt(),
                correlationId, causationId);
    }
}

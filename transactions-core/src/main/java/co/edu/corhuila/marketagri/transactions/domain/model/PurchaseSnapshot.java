package co.edu.corhuila.marketagri.transactions.domain.model;

import java.util.UUID;

/**
 * What the saga sends to create a payment (I-06): the conversation (I-05) and the reservation
 * (I-03) frozen at that moment (D-C31). A value object: two snapshots are the same request exactly
 * when they are equal, which is how a replay is told apart from a {@code TRANSACTION_MISMATCH}.
 */
public record PurchaseSnapshot(UUID conversationId, UUID buyerId, String buyerName, UUID producerId,
                               String producerName, UUID productId, String productName,
                               Quantity quantity, ProductUnit unit, Money unitPrice) {

    /** D-C11: the server computes the amount; the client never sends it. */
    public Money amount() {
        return unitPrice.times(quantity);
    }
}

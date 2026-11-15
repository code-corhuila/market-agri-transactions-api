package co.edu.corhuila.marketagri.transactions.application.port.in;

import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Quantity;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

/** The checkout saga creates the payment (I-06, HU-TXN-001). Idempotent on the caller and the key (D-C35). */
public interface CreatePayment {

    /** Creates the payment, or answers the one created earlier with the same key and body (rows 2–4 of the card). */
    Payment create(Command command);

    /** {@code callerId} is the {@code sub} of the saga's service token, the owner of the key. */
    record Command(UUID callerId, String idempotencyKey, UUID transactionId, UUID conversationId,
                   UUID buyerId, String buyerName, UUID producerId, String producerName,
                   UUID productId, String productName, BigDecimal quantity, ProductUnit unit,
                   long unitPriceCents) {

        public PurchaseSnapshot purchase() {
            return new PurchaseSnapshot(conversationId, buyerId, buyerName, producerId, producerName, productId,
                    productName, new Quantity(quantity), unit, Money.ofCents(unitPriceCents));
        }
    }

    /** {@code created} is false on a replay: the HTTP adapter answers 200 instead of 201. */
    record Payment(Transaction transaction, URI checkoutUrl, boolean created) {
    }
}

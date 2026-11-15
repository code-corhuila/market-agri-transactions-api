package co.edu.corhuila.marketagri.transactions.adapter.in.http;

import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code TransactionView} of api-contract §4.4, never the aggregate. Money in integer centavos
 * (ADR-029). {@code ledger} stays {@code null} until the ledger entry exists (D4b, D-C14).
 */
public record TransactionView(UUID id, UUID conversationId, UUID productId, String productName, UUID buyerId,
                              String buyerName, UUID producerId, String producerName, BigDecimal quantity,
                              String unit, long unitPriceCents, long amountCents, String currency, String status,
                              String paymentMethod, Instant createdAt, Instant confirmedAt, Instant failedAt,
                              Object ledger) {

    private static final String CURRENCY = "COP";

    static TransactionView of(Transaction transaction) {
        PurchaseSnapshot purchase = transaction.purchase();
        return new TransactionView(transaction.id(), purchase.conversationId(), purchase.productId(),
                purchase.productName(), purchase.buyerId(), purchase.buyerName(), purchase.producerId(),
                purchase.producerName(), purchase.quantity().value(), purchase.unit().name(),
                purchase.unitPrice().cents(), transaction.amount().cents(), CURRENCY, transaction.status().name(),
                transaction.paymentMethod() == null ? null : transaction.paymentMethod().name(),
                transaction.createdAt(), transaction.confirmedAt(), transaction.failedAt(), null);
    }
}

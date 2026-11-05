package co.edu.corhuila.marketagri.transactions.adapter.out.persistence;

import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionCreations;
import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentMethod;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Quantity;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionInProgressException;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link TransactionCreations} on {@code transactions.payment_transaction} and
 * {@code transactions.idempotency_key} (B2, D-C35), as transactions_app. The database decides the
 * races: the primary keys make a second insert of the same id or key write nothing, and the
 * partial unique index of D-21 refuses a second {@code PENDING} payment for a conversation.
 */
public final class JdbcTransactionCreations implements TransactionCreations {

    /** B2, {@code 10_indexes/001_create_indexes.sql}: one payment in progress per conversation. */
    static final String ONE_PENDING_PER_CONVERSATION = "uq_payment_transaction_pending_conversation";

    private final JdbcClient jdbc;

    public JdbcTransactionCreations(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@code ON CONFLICT (id)} only covers the primary key: any other unique violation still
     * raises, and the one of the partial index becomes {@code TRANSACTION_IN_PROGRESS}.
     */
    @Override
    public boolean add(Transaction transaction) {
        PurchaseSnapshot purchase = transaction.purchase();
        try {
            return jdbc.sql("""
                    INSERT INTO transactions.payment_transaction (id, conversation_id, product_id, buyer_id,
                        producer_id, product_name, buyer_name, producer_name, quantity, unit, unit_price_cents,
                        amount_cents, status, payment_method, gateway_transaction_id, expires_at, created_at,
                        updated_at, confirmed_at, failed_at)
                    VALUES (:id, :conversationId, :productId, :buyerId, :producerId, :productName, :buyerName,
                        :producerName, :quantity, :unit, :unitPriceCents, :amountCents, :status, :paymentMethod,
                        :gatewayTransactionId, :expiresAt, :createdAt, :createdAt, :confirmedAt, :failedAt)
                    ON CONFLICT (id) DO NOTHING""")
                    .param("id", transaction.id())
                    .param("conversationId", purchase.conversationId())
                    .param("productId", purchase.productId())
                    .param("buyerId", purchase.buyerId())
                    .param("producerId", purchase.producerId())
                    .param("productName", purchase.productName())
                    .param("buyerName", purchase.buyerName())
                    .param("producerName", purchase.producerName())
                    .param("quantity", purchase.quantity().value())
                    .param("unit", purchase.unit().name())
                    .param("unitPriceCents", purchase.unitPrice().cents())
                    .param("amountCents", transaction.amount().cents())
                    .param("status", transaction.status().name())
                    .param("paymentMethod", nameOf(transaction.paymentMethod()))
                    .param("gatewayTransactionId", transaction.gatewayTransactionId())
                    .param("expiresAt", utc(transaction.expiresAt()))
                    .param("createdAt", utc(transaction.createdAt()))
                    .param("confirmedAt", utc(transaction.confirmedAt()))
                    .param("failedAt", utc(transaction.failedAt()))
                    .update() == 1;
        } catch (DuplicateKeyException violation) {
            if (violation.getCause() instanceof PSQLException cause && cause.getServerErrorMessage() != null
                    && ONE_PENDING_PER_CONVERSATION.equals(cause.getServerErrorMessage().getConstraint())) {
                throw new TransactionInProgressException(purchase.conversationId());
            }
            throw violation;
        }
    }

    @Override
    public boolean claimKey(UUID callerId, String idempotencyKey, UUID transactionId) {
        // A concurrent claim of the same key waits for the first transaction, then inserts nothing.
        return jdbc.sql("""
                INSERT INTO transactions.idempotency_key (owner_id, key_value, transaction_id)
                VALUES (:ownerId, :keyValue, :transactionId)
                ON CONFLICT (owner_id, key_value) DO NOTHING""")
                .param("ownerId", callerId)
                .param("keyValue", idempotencyKey)
                .param("transactionId", transactionId)
                .update() == 1;
    }

    @Override
    public Optional<Transaction> transactionForKey(UUID callerId, String idempotencyKey) {
        return jdbc.sql("""
                SELECT t.* FROM transactions.idempotency_key k
                JOIN transactions.payment_transaction t ON t.id = k.transaction_id
                WHERE k.owner_id = :ownerId AND k.key_value = :keyValue""")
                .param("ownerId", callerId)
                .param("keyValue", idempotencyKey)
                .query(JdbcTransactionCreations::transaction)
                .optional();
    }

    /** One row back to the aggregate; its constructor refuses a row that breaks the rules. */
    static Transaction transaction(ResultSet row, int rowNumber) throws SQLException {
        PurchaseSnapshot purchase = new PurchaseSnapshot(row.getObject("conversation_id", UUID.class),
                row.getObject("buyer_id", UUID.class), row.getString("buyer_name"),
                row.getObject("producer_id", UUID.class), row.getString("producer_name"),
                row.getObject("product_id", UUID.class), row.getString("product_name"),
                new Quantity(row.getBigDecimal("quantity")), ProductUnit.valueOf(row.getString("unit")),
                Money.ofCents(row.getLong("unit_price_cents")));
        String method = row.getString("payment_method");
        return new Transaction(row.getObject("id", UUID.class), purchase, Money.ofCents(row.getLong("amount_cents")),
                TransactionStatus.valueOf(row.getString("status")),
                method == null ? null : PaymentMethod.valueOf(method),
                row.getString("gateway_transaction_id"), instant(row, "created_at"), instant(row, "expires_at"),
                instant(row, "confirmed_at"), instant(row, "failed_at"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static String nameOf(PaymentMethod method) {
        return method == null ? null : method.name();
    }
}

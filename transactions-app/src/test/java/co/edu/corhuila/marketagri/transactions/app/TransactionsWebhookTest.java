package co.edu.corhuila.marketagri.transactions.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.PaymentMethod;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Quantity;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionEvent;
import co.edu.corhuila.marketagri.transactions.domain.model.TransactionStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * E-43 over HTTP with the ports in memory (api-contract, amended 2026-11-04; Annex A T8, T8b):
 * anonymous, the checksum is the authentication, and every validly signed event answers 200.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class TransactionsWebhookTest extends TransactionsSpringTest {

    static final String PATH = "/api/v1/transactions/webhooks/payment-gateway";
    /** The same value as payment.wompi.events-secret in the test properties. */
    static final String SECRET = "test_events_only_for_the_tests";
    private static final long AMOUNT = 8_500_000;

    @TestConfiguration
    static class InMemory {
        @Bean
        @Primary
        InMemoryTransactions inMemoryTransactions() {
            return new InMemoryTransactions();
        }
    }

    @Autowired
    private MockMvc http;

    @Autowired
    private InMemoryTransactions store;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void onePendingPayment() {
        store.clear();
        PurchaseSnapshot purchase = new PurchaseSnapshot(UUID.randomUUID(), UUID.randomUUID(), "Carlos Pérez",
                UUID.randomUUID(), "Ana Ruiz", UUID.randomUUID(), "Café pergamino seco", Quantity.of("1"),
                ProductUnit.ARROBA, Money.ofCents(AMOUNT));
        store.put(Transaction.open(id, purchase, Instant.now()));
    }

    static String signed(UUID reference, String wompiId, String status, long amountCents) throws Exception {
        long timestamp = 1_762_600_000L;
        String chain = wompiId + status + amountCents + timestamp + SECRET;
        String checksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(chain.getBytes(StandardCharsets.UTF_8)));
        return """
                {"event":"transaction.updated","data":{"transaction":{"id":"%s","amount_in_cents":%d,
                 "reference":"%s","currency":"COP","payment_method_type":"CARD","status":"%s"}},
                 "environment":"test","signature":{"properties":["transaction.id","transaction.status",
                 "transaction.amount_in_cents"],"checksum":"%s"},"timestamp":%d,
                 "sent_at":"2026-11-08T12:00:00.000Z"}""".formatted(wompiId, amountCents, reference, status,
                checksum.toUpperCase(), timestamp);
    }

    private ResultActions send(String body) throws Exception {
        return http.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Correlation-Id", "wompi-1"));
    }

    @Test
    void anApprovedEventConfirmsWithoutAnyToken() throws Exception {
        send(signed(id, "w-1", "APPROVED", AMOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true));

        Transaction confirmed = store.findById(id).orElseThrow();
        assertThat(confirmed.status()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(confirmed.paymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(store.ledger()).singleElement().isEqualTo(confirmed.ledgerEntry());
        assertThat(store.outbox()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(TransactionEvent.Type.TRANSACTION_CONFIRMED);
            assertThat(event.correlationId()).isEqualTo("wompi-1");
        });
    }

    @Test
    void aDeclinedEventFails() throws Exception {
        send(signed(id, "w-1", "DECLINED", AMOUNT)).andExpect(status().isOk());

        assertThat(store.findById(id).orElseThrow().status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(store.outbox()).singleElement()
                .satisfies(event -> assertThat(event.type()).isEqualTo(TransactionEvent.Type.TRANSACTION_FAILED));
    }

    @Test
    void aTamperedEventIs400InvalidSignatureAndChangesNothing() throws Exception {
        send(signed(id, "w-1", "DECLINED", AMOUNT).replace("\"DECLINED\"", "\"APPROVED\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SIGNATURE"))
                .andExpect(jsonPath("$.message").value("Firma inválida."));

        assertThat(store.findById(id).orElseThrow().status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(store.outbox()).isEmpty();
    }

    @Test
    void anotherAmountAnswers200KeepsItPendingAndWarns(CapturedOutput log) throws Exception {
        send(signed(id, "w-1", "APPROVED", AMOUNT + 1)).andExpect(status().isOk());

        assertThat(store.findById(id).orElseThrow().status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(store.outbox()).isEmpty();
        assertThat(log.getOut()).contains("REFUSED_BY_GUARD").contains(id.toString());
    }

    @Test
    void aRedeliveredOrUnknownEventAnswers200AndChangesNothing() throws Exception {
        send(signed(id, "w-1", "APPROVED", AMOUNT)).andExpect(status().isOk());
        send(signed(id, "w-1", "APPROVED", AMOUNT)).andExpect(status().isOk());
        send(signed(UUID.randomUUID(), "w-2", "APPROVED", AMOUNT)).andExpect(status().isOk());

        assertThat(store.outbox()).hasSize(1);
    }
}

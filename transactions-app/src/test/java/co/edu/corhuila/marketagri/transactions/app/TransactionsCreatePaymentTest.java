package co.edu.corhuila.marketagri.transactions.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * I-06 over HTTP with the ports in memory and the fake provider (Anexo C, "HTTP"): the card of
 * api-contract §4.4 and AC1, AC3, AC4 and AC5 of the sub-task (transactions-api#7).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransactionsCreatePaymentTest extends TransactionsSpringTest {

    static final String PATH = "/internal/v1/transactions";

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

    @BeforeEach
    void empty() {
        store.clear();
    }

    static String body(UUID id, UUID conversation, String quantity, String unitPriceCents) {
        return """
                {"transactionId":"%s","conversationId":"%s",
                 "buyerId":"b0000000-0000-4000-8000-000000000001","buyerName":"Carlos Pérez",
                 "producerId":"a1b2c3d4-4444-0000-0000-000000000001","producerName":"Ana Ruiz",
                 "productId":"d0000000-0000-4000-8000-000000000001","productName":"Café pergamino seco",
                 "quantity":%s,"unit":"ARROBA","unitPriceCents":%s}""".formatted(id, conversation, quantity, unitPriceCents);
    }

    static ResultActions create(MockMvc http, String token, String key, String body) throws Exception {
        var request = post(PATH).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return http.perform(request);
    }

    private ResultActions create(UUID id, UUID conversation, String quantity) throws Exception {
        return create(http, TestTokens.service().signed(), id.toString(), body(id, conversation, quantity, "680000"));
    }

    private static void expectError(ResultActions result, int status, String error, String field) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.error").value(error));
        if (field != null) {
            result.andExpect(jsonPath("$.details[0].field").value(field));
        }
    }

    @Test
    void theSagaCreatesAPendingPaymentWithTheComputedAmountAndItsLink() throws Exception {
        UUID id = UUID.randomUUID();

        create(id, UUID.randomUUID(), "12.5")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transaction.id").value(id.toString()))
                .andExpect(jsonPath("$.transaction.amountCents").value(8_500_000))
                .andExpect(jsonPath("$.transaction.unitPriceCents").value(680_000))
                .andExpect(jsonPath("$.transaction.currency").value("COP"))
                .andExpect(jsonPath("$.transaction.status").value("PENDING"))
                .andExpect(jsonPath("$.transaction.paymentMethod").value(nullValue()))
                .andExpect(jsonPath("$.transaction.ledger").value(nullValue()))
                .andExpect(jsonPath("$.checkoutUrl").value("http://localhost:8001/transacciones/" + id));
    }

    @Test
    void theSameKeyAndBodyIs200WithTheOriginalAndNothingNew() throws Exception {
        UUID id = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        String createdAt = create(id, conversation, "12.5").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String replay = create(id, conversation, "12.50").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(replay).isEqualTo(createdAt);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void theSameKeyWithAnotherBodyIsAMismatch() throws Exception {
        UUID id = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        create(id, conversation, "12.5").andExpect(status().isCreated());

        create(id, conversation, "3")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("TRANSACTION_MISMATCH"))
                .andExpect(jsonPath("$.message").value("La transacción ya existe con otros datos."));
    }

    @Test
    void aSecondPaymentForTheConversationIsInProgress() throws Exception {
        UUID conversation = UUID.randomUUID();
        create(UUID.randomUUID(), conversation, "1").andExpect(status().isCreated());

        create(UUID.randomUUID(), conversation, "1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("TRANSACTION_IN_PROGRESS"))
                .andExpect(jsonPath("$.message").value("Ya hay un pago en curso para esta conversación."));
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void aUserTokenIs403AndNoTokenIs401() throws Exception {
        UUID id = UUID.randomUUID();
        String body = body(id, UUID.randomUUID(), "1", "680000");

        expectError(create(http, TestTokens.user("BUYER").signed(), id.toString(), body), 403, "FORBIDDEN", null);
        expectError(create(http, null, id.toString(), body), 401, "UNAUTHORIZED", null);
        assertThat(store.size()).isEqualTo(0);
    }

    @Test
    void aMalformedBodyIs400NamingTheField() throws Exception {
        String service = TestTokens.service().signed();
        UUID id = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();

        expectError(create(http, service, id.toString(), body(id, conversation, "0", "680000")), 400,
                "VALIDATION_ERROR", "quantity");
        expectError(create(http, service, id.toString(), body(id, conversation, "1.005", "680000")), 400,
                "VALIDATION_ERROR", "quantity");
        expectError(create(http, service, id.toString(), body(id, conversation, "1", "0")), 400,
                "VALIDATION_ERROR", "unitPriceCents");
        expectError(create(http, service, id.toString(), body(id, conversation, "1", "12.5")), 400,
                "VALIDATION_ERROR", null);
        expectError(create(http, service, id.toString(), body(id, conversation, "1", "680000")
                .replace("\"ARROBA\"", "\"KILO\"")), 400, "VALIDATION_ERROR", null);
        expectError(create(http, service, id.toString(), body(id, conversation, "1", "680000")
                .replace("\"Ana Ruiz\"", "\"  \"")), 400, "VALIDATION_ERROR", "producerName");
        expectError(create(http, service, id.toString(), body(id, conversation, "1", "680000")
                .replace("\"transactionId\":\"" + id + "\",", "")), 400, "VALIDATION_ERROR", "transactionId");
        assertThat(store.size()).isEqualTo(0);
    }

    @Test
    void theIdempotencyKeyIsRequiredWithItsLength() throws Exception {
        String service = TestTokens.service().signed();
        UUID id = UUID.randomUUID();
        String body = body(id, UUID.randomUUID(), "1", "680000");

        expectError(create(http, service, null, body), 400, "VALIDATION_ERROR", "Idempotency-Key");
        expectError(create(http, service, "short", body), 400, "VALIDATION_ERROR", "Idempotency-Key");
    }

    @Test
    void anAmountThatRoundsToZeroCentavosIs400() throws Exception {
        UUID id = UUID.randomUUID();

        expectError(create(http, TestTokens.service().signed(), id.toString(),
                body(id, UUID.randomUUID(), "0.01", "1")), 400, "VALIDATION_ERROR", null);
    }
}

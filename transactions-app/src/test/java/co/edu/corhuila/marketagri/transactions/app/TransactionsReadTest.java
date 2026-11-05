package co.edu.corhuila.marketagri.transactions.app;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.marketagri.transactions.domain.model.Money;
import co.edu.corhuila.marketagri.transactions.domain.model.ProductUnit;
import co.edu.corhuila.marketagri.transactions.domain.model.PurchaseSnapshot;
import co.edu.corhuila.marketagri.transactions.domain.model.Quantity;
import co.edu.corhuila.marketagri.transactions.domain.model.Transaction;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** E-41 and E-42 over HTTP with the ports in memory: AC6 and AC7 of the sub-task (transactions-api#7). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TransactionsCreatePaymentTest.InMemory.class)
class TransactionsReadTest extends TransactionsSpringTest {

    private static final String PATH = "/api/v1/transactions";
    private static final UUID CARLOS = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID ANA = UUID.fromString("a1b2c3d4-4444-0000-0000-000000000001");

    @Autowired
    private MockMvc http;

    @Autowired
    private InMemoryTransactions store;

    @BeforeEach
    void empty() {
        store.clear();
    }

    private Transaction stored(UUID buyer, UUID producer, String createdAt) {
        Transaction transaction = Transaction.open(UUID.randomUUID(), new PurchaseSnapshot(UUID.randomUUID(), buyer,
                "Carlos Pérez", producer, "Ana Ruiz", UUID.randomUUID(), "Café pergamino seco", Quantity.of("12.5"),
                ProductUnit.ARROBA, Money.ofCents(680_000)), Instant.parse(createdAt));
        store.add(transaction);
        return transaction;
    }

    private static String as(String role, UUID user) {
        return "Bearer " + TestTokens.user(role).claim("sub", user.toString()).signed();
    }

    private ResultActions read(String path, String authorization) throws Exception {
        var request = get(path);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return http.perform(request);
    }

    @Test
    void theBuyerAndTheProducerReadThePayment() throws Exception {
        Transaction transaction = stored(CARLOS, ANA, "2026-11-04T15:00:00Z");

        read(PATH + "/" + transaction.id(), as("BUYER", CARLOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transaction.id().toString()))
                .andExpect(jsonPath("$.amountCents").value(8_500_000))
                .andExpect(jsonPath("$.paymentMethod").value(nullValue()))
                .andExpect(jsonPath("$.ledger").value(nullValue()));
        read(PATH + "/" + transaction.id(), as("PRODUCER", ANA)).andExpect(status().isOk());
    }

    @Test
    void someoneElseIsNotAParticipantAndAnUnknownIdIsNotFound() throws Exception {
        Transaction transaction = stored(CARLOS, ANA, "2026-11-04T15:00:00Z");

        read(PATH + "/" + transaction.id(), as("BUYER", UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NOT_A_PARTICIPANT"))
                .andExpect(jsonPath("$.message").value("No participa en esta transacción."));
        read(PATH + "/" + UUID.randomUUID(), as("BUYER", CARLOS))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("TRANSACTION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("La transacción no existe."));
    }

    @Test
    void aMalformedIdIs400AndOnlyBuyersAndProducersMayRead() throws Exception {
        read(PATH + "/not-a-uuid", as("BUYER", CARLOS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("id"));
        read(PATH + "/" + UUID.randomUUID(), as("ADMIN", UUID.randomUUID())).andExpect(status().isForbidden());
        read(PATH + "/mine", as("ADMIN", UUID.randomUUID())).andExpect(status().isForbidden());
        read(PATH + "/mine", null).andExpect(status().isUnauthorized());
    }

    @Test
    void aBuyerListsTheirPurchasesAndAProducerTheirSalesNewestFirst() throws Exception {
        Transaction older = stored(CARLOS, ANA, "2026-11-04T15:00:00Z");
        Transaction newer = stored(CARLOS, ANA, "2026-11-04T16:00:00Z");
        Transaction otherBuyer = stored(UUID.randomUUID(), ANA, "2026-11-04T17:00:00Z");

        read(PATH + "/mine", as("BUYER", CARLOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(newer.id().toString()))
                .andExpect(jsonPath("$.data[1].id").value(older.id().toString()))
                .andExpect(jsonPath("$.data[0].ledger").value(nullValue()))
                .andExpect(jsonPath("$.meta.total").value(2));
        read(PATH + "/mine", as("PRODUCER", ANA))
                .andExpect(jsonPath("$.data[0].id").value(otherBuyer.id().toString()))
                .andExpect(jsonPath("$.meta.total").value(3));
    }

    @Test
    void theListIsPaginatedAndFilteredByStatus() throws Exception {
        stored(CARLOS, ANA, "2026-11-04T15:00:00Z");
        Transaction newer = stored(CARLOS, ANA, "2026-11-04T16:00:00Z");

        read(PATH + "/mine?page=2&limit=1", as("BUYER", CARLOS))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.limit").value(1))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
        read(PATH + "/mine?status=PENDING", as("BUYER", CARLOS)).andExpect(jsonPath("$.meta.total").value(2));
        read(PATH + "/mine?status=CONFIRMED", as("BUYER", CARLOS))
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.total").value(0));
        read(PATH + "/mine?limit=1", as("BUYER", CARLOS)).andExpect(jsonPath("$.data[0].id").value(newer.id().toString()));
    }

    @Test
    void anUnknownOrOutOfRangeParameterIs400NamingIt() throws Exception {
        String carlos = as("BUYER", CARLOS);

        read(PATH + "/mine?status=pending", carlos).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("status"));
        read(PATH + "/mine?limit=101", carlos).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("limit"));
        read(PATH + "/mine?page=0", carlos).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("page"));
        read(PATH + "/mine?buyerId=" + ANA, carlos).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("buyerId"));
    }
}

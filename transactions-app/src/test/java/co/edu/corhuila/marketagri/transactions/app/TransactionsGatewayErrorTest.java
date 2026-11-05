package co.edu.corhuila.marketagri.transactions.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** I-06 row 4: wompi without its keys answers 502, the payment stays FAILED and a replay gets the same 502. */
@SpringBootTest(properties = "payment.gateway=wompi")
@AutoConfigureMockMvc
@Import(TransactionsCreatePaymentTest.InMemory.class)
class TransactionsGatewayErrorTest extends TransactionsSpringTest {

    @Autowired
    private MockMvc http;

    @Autowired
    private InMemoryTransactions store;

    private ResultActions create(UUID id, String body) throws Exception {
        return TransactionsCreatePaymentTest.create(http, TestTokens.service().signed(), id.toString(), body);
    }

    @Test
    void withoutTheKeysOfWompiThePaymentFailsWith502AndSoDoesItsReplay() throws Exception {
        store.clear();
        UUID id = UUID.randomUUID();
        String body = TransactionsCreatePaymentTest.body(id, UUID.randomUUID(), "1", "680000");

        for (int attempt = 0; attempt < 2; attempt++) {
            create(id, body).andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error").value("PAYMENT_GATEWAY_ERROR"))
                    .andExpect(jsonPath("$.message").value("No fue posible iniciar el pago. Intente de nuevo."));
        }
        assertThat(store.size()).isEqualTo(1);
    }
}

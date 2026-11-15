package co.edu.corhuila.marketagri.transactions.app;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** PAYMENT_GATEWAY=wompi with its keys (placeholders, never real ones): the link is Wompi's Web Checkout. */
@SpringBootTest(properties = {"payment.gateway=wompi", "payment.wompi.public-key=pub_test_placeholder",
        "payment.wompi.integrity-secret=test_integrity_placeholder"})
@AutoConfigureMockMvc
@Import(TransactionsCreatePaymentTest.InMemory.class)
class TransactionsWompiTest extends TransactionsSpringTest {

    @Autowired
    private MockMvc http;

    @Test
    void theLinkIsTheSignedWebCheckoutOfWompi() throws Exception {
        UUID id = UUID.randomUUID();

        TransactionsCreatePaymentTest.create(http, TestTokens.service().signed(), id.toString(),
                        TransactionsCreatePaymentTest.body(id, UUID.randomUUID(), "12.5", "680000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkoutUrl").value(startsWith("https://checkout.wompi.co/p/?public-key=pub_test_placeholder")))
                .andExpect(jsonPath("$.checkoutUrl").value(containsString("&amount-in-cents=8500000&reference=" + id)))
                .andExpect(jsonPath("$.checkoutUrl").value(containsString("&signature:integrity=")));
    }
}

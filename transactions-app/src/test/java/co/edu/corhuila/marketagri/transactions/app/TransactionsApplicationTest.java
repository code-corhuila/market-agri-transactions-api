package co.edu.corhuila.marketagri.transactions.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** The composition root starts and the liveness endpoint answers without a token (E-60). */
@SpringBootTest
@AutoConfigureMockMvc
class TransactionsApplicationTest {

    @Autowired
    private MockMvc http;

    @Test
    void healthAnswersUpWithoutToken() throws Exception {
        http.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}

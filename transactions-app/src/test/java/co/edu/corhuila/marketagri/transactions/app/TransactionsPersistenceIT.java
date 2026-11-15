package co.edu.corhuila.marketagri.transactions.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** The pool against the real schema: transactions_app, its search_path and the limits of Anexo C. */
@SpringBootTest
@AutoConfigureMockMvc
class TransactionsPersistenceIT extends TransactionsIntegrationTest {

    @Autowired
    private MockMvc http;

    @Autowired
    private JdbcTemplate jdbc;

    /** The integration tests have a database and no broker, so readiness is 503 with the database UP. */
    @Test
    void readinessReportsTheDatabaseUp() throws Exception {
        http.perform(get("/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.checks.database").value("UP"))
                .andExpect(jsonPath("$.checks.broker").value("DOWN"));
    }

    @Test
    void connectsAsTransactionsAppInsideTheTransactionsSchema() {
        Map<String, Object> session = jdbc.queryForMap("SELECT current_user AS who, current_schema() AS schema");

        assertThat(session).containsEntry("who", "transactions_app").containsEntry("schema", "transactions");
    }

    @Test
    void seesTheTablesOfTransactionsDb() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transaction", Long.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_key", Long.class)).isNotNull();
    }

    @Test
    void everyConnectionCarriesTheStatementTimeout() {
        assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class)).isEqualTo("5s");
    }

    @Test
    void aStatementLongerThanTheLimitIsCancelled() {
        assertThatThrownBy(() -> jdbc.execute("SELECT pg_sleep(6)"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("statement timeout");
    }
}

package co.edu.corhuila.marketagri.transactions.app;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP checks of Anexo C 5.3.12 that the scaffold can already satisfy: the error body
 * {error, message, details, traceId} and the correlation id. Token checks arrive with D2.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransactionsHttpTest {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc http;

    @Test
    void receivedCorrelationIdIsReturnedAndUsedAsTraceId() throws Exception {
        http.perform(get("/no-such-route").header("X-Correlation-Id", "e2e-saga-1"))
                .andExpect(header().string("X-Correlation-Id", "e2e-saga-1"))
                .andExpect(jsonPath("$.traceId").value("e2e-saga-1"));
    }

    @Test
    void missingCorrelationIdIsGenerated() throws Exception {
        http.perform(get("/health"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void unsafeCorrelationIdIsReplaced() throws Exception {
        http.perform(get("/health").header("X-Correlation-Id", "bad id\nforged-log-line"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_PATTERN)));
    }

    @Test
    void unknownRouteIs404WithTheErrorBody() throws Exception {
        http.perform(get("/no-such-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("El recurso solicitado no existe."))
                .andExpect(jsonPath("$.details", hasSize(0)))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void wrongMethodIs405WithTheErrorBody() throws Exception {
        http.perform(delete("/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void malformedJsonIs400ValidationError() throws Exception {
        http.perform(post("/probe").header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reference\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details", hasSize(0)));
    }

    @Test
    void invalidBodyNamesEveryField() throws Exception {
        http.perform(post("/probe").header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reference\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details", hasSize(2)))
                .andExpect(jsonPath("$.details[?(@.field == 'reference')]").exists())
                .andExpect(jsonPath("$.details[?(@.field == 'amountCents')]").exists());
    }

    @Test
    void missingIdempotencyKeyIsNamedInDetails() throws Exception {
        http.perform(post("/probe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\": \"r-1\", \"amountCents\": 2500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("Idempotency-Key"));
    }

    @Test
    void malformedIdIs400ValidationError() throws Exception {
        http.perform(get("/probe/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("id"));
    }

    @Test
    void unexpectedErrorIsNeutral500() throws Exception {
        http.perform(get("/probe/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Ocurrió un error inesperado."))
                .andExpect(content().string(not(containsString("SELECT"))));
    }
}

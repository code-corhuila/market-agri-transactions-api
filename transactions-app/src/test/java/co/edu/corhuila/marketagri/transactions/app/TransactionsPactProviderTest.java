package co.edu.corhuila.marketagri.transactions.app;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import co.edu.corhuila.marketagri.transactions.application.port.in.CreatePayment;
import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentGateway;
import co.edu.corhuila.marketagri.transactions.application.usecase.CreatePaymentService;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.apache.hc.core5.http.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Verifies workflow's I-06 Pact V4 against the real HTTP adapter. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Provider("transactions-api")
@PactFolder("pacts")
class TransactionsPactProviderTest extends TransactionsSpringTest {
    private static final UUID TRANSACTION = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @TestConfiguration
    static class InMemory {
        @Bean @Primary InMemoryTransactions pactTransactions() { return new InMemoryTransactions(); }
        @Bean @Primary PaymentGateway pactGateway() { return transaction ->
                URI.create("https://checkout.wompi.co/p/?reference=" + transaction.id()); }
        @Bean @Primary CreatePayment pactCreatePayment(InMemoryTransactions transactions, PaymentGateway gateway) {
            return new CreatePaymentService(transactions, gateway, transactions,
                    Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC));
        }
    }

    @Value("${local.server.port}") private int port;
    @Autowired private InMemoryTransactions transactions;

    @BeforeEach void target(PactVerificationContext context) { context.setTarget(new HttpTestTarget("localhost", port)); }
    @State("the checkout does not have a payment yet") void noPayment() { transactions.clear(); }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifies(PactVerificationContext context, HttpRequest request) {
        request.setHeader("Authorization", "Bearer " + TestTokens.service().signed());
        context.verifyInteraction();
    }
}

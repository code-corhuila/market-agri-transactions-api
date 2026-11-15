package co.edu.corhuila.marketagri.transactions.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Without the issuer or the public key of auth the service does not start (ADR-020, no default). */
class TransactionsConfigurationTest {

    private final TransactionsConfiguration configuration = new TransactionsConfiguration();

    @Test
    void refusesToStartWithoutAPublicKey() {
        assertThatThrownBy(() -> configuration.tokenVerifier("market-agri-auth", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PUBLIC_KEY");
    }

    @Test
    void refusesToStartWithAnUnknownPaymentGateway() {
        assertThatThrownBy(() -> configuration.paymentGateway("stripe", "http://localhost:8001", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAYMENT_GATEWAY");
        assertThatThrownBy(() -> configuration.paymentGateway("", "http://localhost:8001", "", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesToStartWithoutThePublicBaseUrl() {
        assertThatThrownBy(() -> configuration.paymentGateway("fake", "", "", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PUBLIC_BASE_URL");
    }

    @Test
    void refusesToStartWithoutAnIssuer() {
        assertThatThrownBy(() -> configuration.tokenVerifier(" ", TestTokens.publicKeyOnOneLine(), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT_ISSUER");
    }
}

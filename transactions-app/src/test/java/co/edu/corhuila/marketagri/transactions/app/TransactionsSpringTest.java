package co.edu.corhuila.marketagri.transactions.app;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base of every Spring test of this module: the composition root does not start without the
 * public key of auth, so each run gets the one of {@link TestTokens}, generated while it runs.
 */
abstract class TransactionsSpringTest {

    @DynamicPropertySource
    static void tokenKey(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", TestTokens::publicKeyOnOneLine);
    }
}

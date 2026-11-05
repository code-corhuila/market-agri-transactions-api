package co.edu.corhuila.marketagri.transactions.app;

import co.edu.corhuila.marketagri.transactions.adapter.in.http.PublicKeys;
import co.edu.corhuila.marketagri.transactions.adapter.in.http.Rs256Verifier;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The only place that knows the concrete types: use cases of the core are wired to the
 * outbound adapters here, with {@code @Bean} methods.
 */
@Configuration
public class TransactionsConfiguration {

    /**
     * Validator of the tokens of auth (ADR-020): the issuer and the public key by configuration,
     * JWT_PUBLIC_KEY first, JWT_PUBLIC_KEY_FILE otherwise. Without a key the service does not start.
     */
    @Bean
    Rs256Verifier tokenVerifier(@Value("${jwt.issuer}") String issuer,
                                @Value("${jwt.public-key:}") String publicKey,
                                @Value("${jwt.public-key-file:}") String publicKeyFile) {
        RSAPublicKey key;
        if (!publicKey.isBlank()) {
            key = PublicKeys.fromPem(publicKey);
        } else if (!publicKeyFile.isBlank()) {
            key = PublicKeys.fromFile(Path.of(publicKeyFile));
        } else {
            throw new IllegalStateException("Set JWT_PUBLIC_KEY or JWT_PUBLIC_KEY_FILE");
        }
        return new Rs256Verifier(key, issuer, Clock.systemUTC());
    }
}

package co.edu.corhuila.marketagri.transactions.app;

import co.edu.corhuila.marketagri.transactions.adapter.in.http.PublicKeys;
import co.edu.corhuila.marketagri.transactions.adapter.in.http.Rs256Verifier;
import co.edu.corhuila.marketagri.transactions.adapter.out.persistence.JdbcDatabaseCheck;
import co.edu.corhuila.marketagri.transactions.adapter.out.persistence.JdbcTransactionCreations;
import co.edu.corhuila.marketagri.transactions.adapter.out.payment.FakePaymentGateway;
import co.edu.corhuila.marketagri.transactions.adapter.out.payment.RedirectUrls;
import co.edu.corhuila.marketagri.transactions.adapter.out.payment.WompiPaymentGateway;
import co.edu.corhuila.marketagri.transactions.adapter.out.persistence.SpringUnitOfWork;
import co.edu.corhuila.marketagri.transactions.application.port.in.CreatePayment;
import co.edu.corhuila.marketagri.transactions.application.port.out.PaymentGateway;
import co.edu.corhuila.marketagri.transactions.application.port.out.ReadinessCheck;
import co.edu.corhuila.marketagri.transactions.application.port.out.TransactionCreations;
import co.edu.corhuila.marketagri.transactions.application.port.out.UnitOfWork;
import co.edu.corhuila.marketagri.transactions.application.usecase.CreatePaymentService;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    /** E-61: transactions answers only with its database up ({@code broker} joins with the relay, D4a). */
    @Bean
    ReadinessCheck databaseCheck(DataSource dataSource) {
        return new JdbcDatabaseCheck(dataSource);
    }

    @Bean
    TransactionCreations transactionCreations(JdbcClient jdbc) {
        return new JdbcTransactionCreations(jdbc);
    }

    /**
     * The provider of the payments (ADR-019), by {@code PAYMENT_GATEWAY}: {@code wompi} or
     * {@code fake}. Any other value, or none, and the service does not start.
     */
    @Bean
    PaymentGateway paymentGateway(@Value("${payment.gateway:}") String gateway,
                                  @Value("${payment.public-base-url:}") String publicBaseUrl,
                                  @Value("${payment.wompi.public-key:}") String wompiPublicKey,
                                  @Value("${payment.wompi.integrity-secret:}") String wompiIntegritySecret) {
        RedirectUrls redirects = new RedirectUrls(publicBaseUrl);
        return switch (gateway) {
            case "wompi" -> new WompiPaymentGateway(wompiPublicKey, wompiIntegritySecret, redirects);
            case "fake" -> new FakePaymentGateway(redirects);
            default -> throw new IllegalStateException("Set PAYMENT_GATEWAY to wompi or fake, not '" + gateway + "'");
        };
    }

    /** I-06; the clock is UTC, and the aggregate cuts it to seconds. */
    @Bean
    CreatePayment createPayment(TransactionCreations creations, PaymentGateway gateway, UnitOfWork unitOfWork) {
        return new CreatePaymentService(creations, gateway, unitOfWork, Clock.systemUTC());
    }

    /** Spring Boot's transaction manager of the DataSource: the same connection JdbcClient uses. */
    @Bean
    UnitOfWork unitOfWork(PlatformTransactionManager transactionManager) {
        return new SpringUnitOfWork(new TransactionTemplate(transactionManager));
    }
}
